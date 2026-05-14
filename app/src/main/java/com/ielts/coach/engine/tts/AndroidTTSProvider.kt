package com.ielts.coach.engine.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
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

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                uiHandler.post { callback.onSpeakStart() }
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                uiHandler.post { callback.onSpeakComplete() }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                uiHandler.post { callback.onError("TTS utterance error") }
            }
        })
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

        // Poll for file ready, then process
        Thread {
            var attempts = 0
            while (!wavFile.exists() && attempts < 100) {
                Thread.sleep(50)
                attempts++
            }
            if (!wavFile.exists()) {
                uiHandler.post { callback?.onError("TTS file not generated") }
                return@Thread
            }

            try {
                val pcmData = wavToPcm(wavFile)
                val resampledPcm = resampleTo16k(pcmData, getWavSampleRate(wavFile))

                // Push PCM to callback (for Duix.Mobile lip-sync)
                callback?.onPCMData(resampledPcm)

                // Play through AudioTrack
                playPCM(resampledPcm)
            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error", e)
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

    private fun getWavSampleRate(wavFile: File): Int {
        FileInputStream(wavFile).use { fis ->
            val header = ByteArray(44)
            fis.read(header)
            return ByteBuffer.wrap(header, 24, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
        }
    }

    private fun wavToPcm(wavFile: File): ByteArray {
        val bytes = wavFile.readBytes()
        // Skip 44-byte WAV header
        return if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
    }

    private fun resampleTo16k(pcm: ByteArray, sourceRate: Int): ByteArray {
        if (sourceRate == targetSampleRate) return pcm

        val ratio = sourceRate.toDouble() / targetSampleRate
        val sourceSamples = pcm.size / 2
        val targetSamples = (sourceSamples / ratio).toInt()
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

        // Push in chunks for smooth playback
        val chunkSize = 3200 // 100ms of 16kHz 16-bit mono
        var offset = 0
        while (offset < pcmData.size && isSpeaking) {
            val end = (offset + chunkSize).coerceAtMost(pcmData.size)
            audioTrack?.write(pcmData, offset, end - offset)
            offset = end
        }

        // Wait for playback to finish
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
