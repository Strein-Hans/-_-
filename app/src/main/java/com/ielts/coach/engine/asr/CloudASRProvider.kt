package com.ielts.coach.engine.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.internal.concurrent.Task
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class CloudASRProvider(
    private val appId: String,
    private val apiKey: String,
) : ASRProvider {

    private var callback: ASRProvider.ASRCallback? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var webSocket: WebSocket? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun startListening(callback: ASRProvider.ASRCallback) {
        this.callback = callback
        if (isListening) return

        connectWebSocket()
    }

    override fun stopListening() {
        isListening = false
        stopAudioCapture()
        sendEndFrame()
        webSocket?.close(1000, "stop")
        webSocket = null
    }

    override fun release() {
        stopListening()
        callback = null
        client.dispatcher.executorService.shutdown()
    }

    override fun isAvailable(): Boolean = appId.isNotBlank() && apiKey.isNotBlank()

    private fun connectWebSocket() {
        val url = buildAuthUrl()
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                sendStartFrame()
                startAudioCapture()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleResult(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                uiHandler.post {
                    isListening = false
                    stopAudioCapture()
                    callback?.onError(t.message ?: "ASR connection failed")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
            }
        })
    }

    private fun buildAuthUrl(): String {
        val ts = System.currentTimeMillis() / 1000
        return "wss://iat-api.xfyun.cn/v2/iat" +
                "?authorization=Bearer ${apiKey}" +
                "&date=$ts" +
                "&host=iat-api.xfyun.cn"
    }

    private fun sendStartFrame() {
        val json = JSONObject().apply {
            put("common", JSONObject().apply {
                put("app_id", appId)
            })
            put("business", JSONObject().apply {
                put("language", "en_us")
                put("domain", "iat")
                put("accent", "mandarin")
                put("vad_eos", 2000)
                put("dwa", "wpgs")
            })
            put("data", JSONObject().apply {
                put("status", STATUS_FIRST)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", "")
            })
        }
        webSocket?.send(json.toString())
    }

    private fun sendEndFrame() {
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("status", STATUS_LAST)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", "")
            })
        }
        webSocket?.send(json.toString())
    }

    private fun sendAudioData(audioData: ByteArray, status: Int) {
        val base64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("status", status)
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", base64)
            })
        }
        webSocket?.send(json.toString())
    }

    private fun startAudioCapture() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            uiHandler.post { callback?.onError("AudioRecord init failed") }
            return
        }

        isListening = true
        audioRecord?.startRecording()

        Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (isListening) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val bytes = ShortArray(read).let {
                        System.arraycopy(buffer, 0, it, 0, read)
                        val bb = ByteBuffer.allocate(it.size * 2)
                        bb.order(ByteOrder.LITTLE_ENDIAN)
                        for (s in it) bb.putShort(s)
                        bb.array()
                    }
                    sendAudioData(bytes, STATUS_CONTINUE)
                }
            }
        }.start()
    }

    private fun stopAudioCapture() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private fun handleResult(text: String) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val msg = json.optString("message", "Unknown error")
                uiHandler.post { callback?.onError("ASR error $code: $msg") }
                return
            }

            val data = json.optJSONObject("data") ?: return
            val result = data.optJSONObject("result") ?: return
            val ws = result.optJSONArray("ws") ?: return

            val sb = StringBuilder()
            for (i in 0 until ws.length()) {
                val cwArray = ws.optJSONObject(i)?.optJSONArray("cw") ?: continue
                for (j in 0 until cwArray.length()) {
                    sb.append(cwArray.optJSONObject(j)?.optString("w", ""))
                }
            }

            val isEnd = result.optInt("ls", 0) == 1
            val recognized = sb.toString()

            if (recognized.isNotBlank()) {
                uiHandler.post {
                    if (isEnd) {
                        callback?.onFinalResult(recognized)
                    } else {
                        callback?.onPartialResult(recognized)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
        }
    }

    companion object {
        private const val TAG = "CloudASRProvider"
        private const val SAMPLE_RATE = 16000
        private const val STATUS_FIRST = 0
        private const val STATUS_CONTINUE = 1
        private const val STATUS_LAST = 2
    }
}
