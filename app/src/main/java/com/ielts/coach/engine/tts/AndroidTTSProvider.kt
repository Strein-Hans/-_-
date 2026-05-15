package com.ielts.coach.engine.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class AndroidTTSProvider(private val context: Context) : TTSProvider {

    private var tts: TextToSpeech? = null
    private var callback: TTSProvider.TTSCallback? = null
    private var audioTrack: AudioTrack? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isSpeaking = false

    private val targetSampleRate = 16000

    override fun init(callback: TTSProvider.TTSCallback) {
        this.callback = callback
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                Log.d(TAG, "TTS initialized")
            } else {
                uiHandler.post { callback.onError("TTS init failed") }
            }
        }
    }

    override fun speak(text: String) {
        if (isSpeaking) stop()
        isSpeaking = true

        val wavFile = File(context.cacheDir, "tts_output_${System.currentTimeMillis()}.wav")
        val utteranceId = "tts_${System.currentTimeMillis()}"

        val result = tts?.synthesizeToFile(text, Bundle(), wavFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            callback?.onError("synthesizeToFile failed")
            return
        }

        Thread {
            var attempts = 0
            while (!wavFile.exists() && attempts < 100) {
                Thread.sleep(50)
                attempts++
            }
            if (!wavFile.exists()) {
                isSpeaking = false
                uiHandler.post { callback?.onError("TTS file not generated") }
                return@Thread
            }

            Thread.sleep(100)

            try {
                val wavData = WavData.parse(wavFile)
                if (wavData.pcm.isEmpty()) {
                    isSpeaking = false
                    uiHandler.post { callback?.onError("TTS produced empty audio") }
                    return@Thread
                }

                val resampledPcm = resampleTo16k(wavData.pcm, wavData.sampleRate)

                uiHandler.post { callback?.onSpeakStart() }
                callback?.onPCMData(resampledPcm)
                playPCM(resampledPcm)

                isSpeaking = false
                uiHandler.post { callback?.onSpeakComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error", e)
                isSpeaking = false
                uiHandler.post { callback?.onError(e.message ?: "Audio error") }
            } finally {
                wavFile.delete()
            }
        }.start()
    }

    override fun stop() {
        isSpeaking = false
        tts?.stop()
        stopAudioTrack()
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        callback = null
    }

    // Properly parse WAV — finds "data" chunk instead of assuming 44-byte header
    private data class WavData(val sampleRate: Int, val pcm: ByteArray) {
        companion object {
            fun parse(file: File): WavData {
                val bytes = file.readBytes()
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                // Read sample rate from fmt chunk (offset 24 in standard WAV)
                val sampleRate = bb.getInt(24)

                // Find "data" chunk — search for the marker instead of assuming offset 36
                var dataOffset = 12 // skip RIFF header
                while (dataOffset < bytes.size - 8) {
                    val chunkId = String(bytes, dataOffset, 4)
                    val chunkSize = ByteBuffer.wrap(bytes, dataOffset + 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int
                    if (chunkId == "data") {
                        val pcmStart = dataOffset + 8
                        val pcmEnd = (pcmStart + chunkSize).coerceAtMost(bytes.size)
                        return WavData(sampleRate, bytes.copyOfRange(pcmStart, pcmEnd))
                    }
                    dataOffset += 8 + chunkSize
                    // Chunks must be word-aligned
                    if (chunkSize % 2 != 0) dataOffset++
                }

                // Fallback: assume standard 44-byte header
                return WavData(sampleRate, if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else ByteArray(0))
            }
        }
    }

    private fun resampleTo16k(pcm: ByteArray, sourceRate: Int): ByteArray {
        if (sourceRate <= 0 || sourceRate > 96000) return pcm
        if (sourceRate == targetSampleRate) return pcm

        val ratio = sourceRate.toDouble() / targetSampleRate
        val sourceSamples = pcm.size / 2
        if (sourceSamples == 0) return pcm
        val targetSamples = (sourceSamples / ratio).toInt().coerceAtLeast(1)
        val result = ByteArray(targetSamples * 2)
        val bb = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        val srcBB = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until targetSamples) {
            val srcIdx = (i * ratio).toInt()
            if (srcIdx * 2 + 1 < pcm.size) {
                bb.putShort(srcBB.getShort(srcIdx * 2))
            } else {
                bb.putShort(0)
            }
        }
        return result
    }

    private fun playPCM(pcmData: ByteArray) {
        stopAudioTrack()

        val bufSize = pcmData.size.coerceAtLeast(
            AudioTrack.getMinBufferSize(
                targetSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(targetSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        val chunkSize = 3200
        var offset = 0
        while (offset < pcmData.size && isSpeaking) {
            val end = (offset + chunkSize).coerceAtMost(pcmData.size)
            audioTrack?.write(pcmData, offset, end - offset)
            offset = end
        }

        while (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING && isSpeaking) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
        }
        stopAudioTrack()
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
