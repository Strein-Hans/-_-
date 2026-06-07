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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class ServerTTSProvider(
    private val baseUrl: String,
    private val accent: String = "american",
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
                    put("accent", accent)
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

                val mp3Data = response.body?.bytes() ?: byteArrayOf()
                Log.d(TAG, "TTS received ${mp3Data.size} bytes MP3")

                if (!isSpeaking) return@launch

                val pcmData = decodeMp3ToPcm(mp3Data)
                Log.d(TAG, "Decoded to ${pcmData.size} bytes PCM")

                if (!isSpeaking || pcmData.isEmpty()) {
                    isSpeaking = false
                    uiHandler.post { callback?.onSpeakComplete() }
                    return@launch
                }

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

    private fun decodeMp3ToPcm(mp3Data: ByteArray): ByteArray {
        val pcmBuffer = ByteArrayOutputStream()
        val extractor = MediaExtractor()
        val tempFile = java.io.File.createTempFile("tts_", ".mp3")
        try {
            tempFile.writeBytes(mp3Data)
            extractor.setDataSource(tempFile.absolutePath)

            val trackIndex = (0 until extractor.trackCount)
                .firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return byteArrayOf()
            extractor.selectTrack(trackIndex)

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return byteArrayOf()
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inputBufIdx = decoder.dequeueInputBuffer(10_000)
                    if (inputBufIdx >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputBufIdx) ?: break
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufIdx = decoder.dequeueOutputBuffer(info, 10_000)
                if (outputBufIdx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoder.releaseOutputBuffer(outputBufIdx, false)
                        break
                    }
                    val outputBuf = decoder.getOutputBuffer(outputBufIdx) ?: continue
                    val chunk = ByteArray(info.size)
                    outputBuf.position(info.offset)
                    outputBuf.get(chunk)
                    pcmBuffer.write(chunk)
                    decoder.releaseOutputBuffer(outputBufIdx, false)
                }
            }

            decoder.stop()
            decoder.release()
        } catch (e: Exception) {
            Log.e(TAG, "MP3 decode failed", e)
        } finally {
            extractor.release()
            tempFile.delete()
        }

        return pcmBuffer.toByteArray()
    }

    companion object {
        private const val TAG = "ServerTTSProvider"
    }
}
