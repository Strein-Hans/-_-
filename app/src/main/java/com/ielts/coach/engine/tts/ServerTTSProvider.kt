package com.ielts.coach.engine.tts

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ServerTTSProvider(
    private val baseUrl: String,
    private val voiceName: String = "en-US-GuyNeural",
) : TTSProvider {

    private var callback: TTSProvider.TTSCallback? = null
    @Volatile
    private var isSpeaking = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun init(callback: TTSProvider.TTSCallback) {
        this.callback = callback
    }

    override fun speak(text: String) {
        if (isSpeaking) stop()
        isSpeaking = true

        uiHandler.post { callback?.onSpeakStart() }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = JSONObject().apply {
                    put("text", text)
                    put("voice", voiceName)
                }.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url("$baseUrl/tts")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        isSpeaking = false
                        callback?.onError("TTS server error: ${response.code}")
                    }
                    return@launch
                }

                val pcmData = response.body?.bytes() ?: byteArrayOf()
                Log.d(TAG, "TTS received ${pcmData.size} bytes PCM")

                if (!isSpeaking) return@launch

                // Feed PCM in ~40ms chunks (1280 bytes at 16kHz 16bit mono)
                val chunkSize = 1280
                var offset = 0
                while (offset < pcmData.size && isSpeaking) {
                    val end = minOf(offset + chunkSize, pcmData.size)
                    val chunk = pcmData.copyOfRange(offset, end)
                    uiHandler.post { callback?.onPCMData(chunk) }
                    offset = end
                    Thread.sleep(40)
                }

                if (isSpeaking) {
                    isSpeaking = false
                    uiHandler.post { callback?.onSpeakComplete() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server TTS error", e)
                isSpeaking = false
                uiHandler.post { callback?.onError("TTS failed: ${e.message}") }
            }
        }
    }

    override fun stop() {
        isSpeaking = false
    }

    override fun release() {
        stop()
        callback = null
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "ServerTTSProvider"
    }
}
