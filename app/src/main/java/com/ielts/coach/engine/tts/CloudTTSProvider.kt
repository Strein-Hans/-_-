package com.ielts.coach.engine.tts

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class CloudTTSProvider(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val voiceName: String = "aisdavid",
) : TTSProvider {

    private var callback: TTSProvider.TTSCallback? = null
    private var webSocket: WebSocket? = null
    @Volatile
    private var isSpeaking = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun init(callback: TTSProvider.TTSCallback) {
        this.callback = callback
    }

    override fun speak(text: String) {
        if (isSpeaking) stop()
        if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            callback?.onError("iFlytek TTS credentials not configured")
            return
        }

        isSpeaking = true
        val url = buildAuthUrl()
        val request = Request.Builder().url(url).build()

        val speakText = text
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "TTS WebSocket connected")
                uiHandler.post { callback?.onSpeakStart() }
                sendText(ws, speakText)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleAudioData(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "TTS WebSocket failure: ${t.message}")
                isSpeaking = false
                uiHandler.post { callback?.onError("TTS connection failed: ${t.message}") }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "TTS WebSocket closed")
            }
        })
    }

    private fun sendText(ws: WebSocket, text: String) {
        val json = JSONObject().apply {
            put("common", JSONObject().apply {
                put("app_id", appId)
            })
            put("business", JSONObject().apply {
                put("aue", "raw")
                put("auf", "audio/L16;rate=16000")
                put("vcn", voiceName)
                put("speed", 50)
                put("volume", 50)
                put("pitch", 50)
                put("ent", "intp65")
            })
            put("data", JSONObject().apply {
                put("status", 2)
                put("text", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
            })
        }
        ws.send(json.toString())
    }

    private fun handleAudioData(text: String) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val msg = json.optString("message", "Unknown error")
                Log.e(TAG, "TTS error $code: $msg, response: ${text.take(200)}")
                isSpeaking = false
                uiHandler.post { callback?.onError("TTS error $code: $msg") }
                return
            }

            val data = json.optJSONObject("data") ?: return
            val audioBase64 = data.optString("audio", "")
            if (audioBase64.isNotEmpty()) {
                val pcm = Base64.decode(audioBase64, Base64.NO_WRAP)
                Log.d(TAG, "TTS PCM chunk: ${pcm.size} bytes")
                if (pcm.isNotEmpty()) {
                    uiHandler.post { callback?.onPCMData(pcm) }
                }
            }

            val status = data.optInt("status", 1)
            if (status == 2) {
                Log.d(TAG, "TTS audio complete")
                isSpeaking = false
                webSocket?.close(1000, "done")
                webSocket = null
                uiHandler.post { callback?.onSpeakComplete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS parse error", e)
        }
    }

    override fun stop() {
        isSpeaking = false
        try {
            webSocket?.close(1000, "stop")
        } catch (_: Exception) {}
        webSocket = null
    }

    override fun release() {
        stop()
        callback = null
        client.dispatcher.executorService.shutdown()
    }

    private fun buildAuthUrl(): String {
        val host = "tts-api.xfyun.cn"
        val path = "/v2/tts"
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())

        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"
        val signature = hmacSha256Base64(signatureOrigin, apiSecret)

        val authorizationOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(authorizationOrigin.toByteArray(), Base64.NO_WRAP)

        return "wss://$host$path" +
                "?authorization=${URLEncoder.encode(authorization, "UTF-8")}" +
                "&date=${URLEncoder.encode(date, "UTF-8")}" +
                "&host=$host"
    }

    private fun hmacSha256Base64(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray()), Base64.NO_WRAP)
    }

    companion object {
        private const val TAG = "CloudTTSProvider"
    }
}
