package com.ielts.coach.engine.tts

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.TimeUnit

class EdgeTTSProvider(
    private val voiceName: String = "en-US-GuyNeural",
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
        isSpeaking = true

        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")

        val url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4" +
                "&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "Edge TTS connected")
                // Send configuration
                val config = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                        JSONObject().apply {
                            put("context", JSONObject().apply {
                                put("synthesis", JSONObject().apply {
                                    put("audio", JSONObject().apply {
                                        put("metadataoptions", JSONObject().apply {
                                            put("sentenceBoundaryEnabled", "false")
                                            put("wordBoundaryEnabled", "true")
                                        })
                                        put("outputFormat", "audio-16khz-16bit-mono-pcm")
                                    })
                                })
                            })
                        }.toString()
                ws.send(config)

                // Send SSML
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                        "<voice name='$voiceName'>$text</voice></speak>"
                val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.US).format(java.util.Date())};" +
                        "Path:ssml\r\n\r\n$ssml"

                uiHandler.post { callback?.onSpeakStart() }
                ws.send(ssmlMsg)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // Handle text messages (turn.start, response, turn.end)
                if (text.contains("Path:turn.end")) {
                    Log.d(TAG, "Edge TTS audio complete")
                    isSpeaking = false
                    ws.close(1000, "done")
                    webSocket = null
                    uiHandler.post { callback?.onSpeakComplete() }
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // Binary message contains audio data with header
                val data = bytes.toByteArray()
                if (data.size < 2) return

                // First 2 bytes = header length (big-endian)
                val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                if (data.size > headerLen + 2) {
                    val audioData = data.copyOfRange(headerLen + 2, data.size)
                    if (audioData.isNotEmpty()) {
                        uiHandler.post { callback?.onPCMData(audioData) }
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "Edge TTS failure: ${t.message}")
                isSpeaking = false
                uiHandler.post { callback?.onError("TTS failed: ${t.message}") }
            }
        })
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

    companion object {
        private const val TAG = "EdgeTTSProvider"
    }
}
