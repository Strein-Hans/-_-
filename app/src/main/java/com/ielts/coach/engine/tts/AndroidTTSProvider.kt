package com.ielts.coach.engine.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class AndroidTTSProvider(
    private val context: Context,
    private val playAudio: Boolean = true,
) : TTSProvider {

    private var tts: TextToSpeech? = null
    private var callback: TTSProvider.TTSCallback? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var isSpeaking = false
    private var playbackThread: Thread? = null
    private var currentAudioTrack: AudioTrack? = null

    override fun init(callback: TTSProvider.TTSCallback) {
        this.callback = callback
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US

                // Try to find a male English voice to match the male avatar
                val maleVoice = tts?.voices?.firstOrNull {
                    it.locale.language == "en" &&
                        (it.name.contains("male", ignoreCase = true) &&
                            !it.name.contains("female", ignoreCase = true))
                }
                if (maleVoice != null) {
                    tts?.voice = maleVoice
                    Log.d(TAG, "Set male voice: ${maleVoice.name}")
                } else {
                    // Fallback: list available voices for debugging
                    tts?.voices?.filter { it.locale.language == "en" }?.forEach {
                        Log.d(TAG, "Available voice: ${it.name} ${it.locale} features=${it.features}")
                    }
                    Log.d(TAG, "No male voice found, using default")
                }

                Log.d(TAG, "TTS engine initialized")
            } else {
                Log.e(TAG, "TTS engine init failed: $status")
                uiHandler.post { callback.onError("TTS init failed") }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS synthesis started")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS synthesis done, feeding PCM")
                feedPcmFromWav()
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS synthesis error")
                isSpeaking = false
                uiHandler.post { callback.onError("TTS synthesis error") }
            }
        })
    }

    override fun speak(text: String) {
        if (isSpeaking) stop()
        isSpeaking = true
        Log.d(TAG, "speak() called, synthesizing to file")

        val wavFile = File(context.cacheDir, "tts_output.wav")
        wavFile.delete()

        val utteranceId = "tts_${System.currentTimeMillis()}"
        val result = tts?.synthesizeToFile(text, Bundle(), wavFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            callback?.onError("TTS synthesis failed")
        }
    }

    private fun feedPcmFromWav() {
        uiHandler.post { callback?.onSpeakStart() }

        playbackThread = Thread {
            try {
                val wavFile = File(context.cacheDir, "tts_output.wav")
                if (!wavFile.exists()) {
                    isSpeaking = false
                    uiHandler.post { callback?.onError("TTS file not found") }
                    return@Thread
                }

                val wavData = wavFile.readBytes()
                if (wavData.size < 44) {
                    isSpeaking = false
                    uiHandler.post { callback?.onError("TTS file too small") }
                    return@Thread
                }

                val sampleRate = ByteBuffer.wrap(wavData, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val bitsPerSample = ByteBuffer.wrap(wavData, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val channels = ByteBuffer.wrap(wavData, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

                Log.d(TAG, "WAV: ${sampleRate}Hz, ${bitsPerSample}bit, ${channels}ch")

                var dataOffset = 12
                while (dataOffset < wavData.size - 8) {
                    val chunkId = String(wavData, dataOffset, 4)
                    val chunkSize = ByteBuffer.wrap(wavData, dataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (chunkId == "data") {
                        dataOffset += 8
                        break
                    }
                    dataOffset += 8 + chunkSize
                }

                val pcmData = wavData.copyOfRange(dataOffset, wavData.size)
                wavFile.delete()

                // Resample to 16kHz mono for DH SDK
                val mono = if (channels >= 2) toMono(pcmData, bitsPerSample) else pcmData
                val resampled = if (sampleRate != 16000) resample(mono, sampleRate, 16000, bitsPerSample) else mono

                val bytesPerSec = 16000 * 2 // 16kHz, 16bit, mono
                val chunkSize = bytesPerSec * 40 / 1000 // 40ms chunks

                if (playAudio) {
                    // Voice-only mode: play through AudioTrack ourselves
                    val bufSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(16000)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    currentAudioTrack = track
                    track.play()

                    var offset = 0
                    while (offset < resampled.size && isSpeaking) {
                        val end = minOf(offset + chunkSize, resampled.size)
                        val chunk = resampled.copyOfRange(offset, end)
                        track.write(chunk, 0, chunk.size)
                        offset = end
                        Thread.sleep(40)
                    }

                    track.stop()
                    track.release()
                    currentAudioTrack = null
                } else {
                    // DH mode: just feed PCM, DH SDK handles audio playback
                    var offset = 0
                    while (offset < resampled.size && isSpeaking) {
                        val end = minOf(offset + chunkSize, resampled.size)
                        val chunk = resampled.copyOfRange(offset, end)
                        uiHandler.post { callback?.onPCMData(chunk) }
                        offset = end
                        Thread.sleep(40)
                    }
                }

                if (isSpeaking) {
                    isSpeaking = false
                    uiHandler.post { callback?.onSpeakComplete() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PCM feed error", e)
                isSpeaking = false
                currentAudioTrack = null
                uiHandler.post { callback?.onSpeakComplete() }
            }
        }.also { it.start() }
    }

    private fun toMono(stereoData: ByteArray, bitsPerSample: Int): ByteArray {
        if (bitsPerSample != 16) return stereoData
        val monoSamples = stereoData.size / 4
        val mono = ByteArray(monoSamples * 2)
        for (i in 0 until monoSamples) {
            mono[i * 2] = stereoData[i * 4]
            mono[i * 2 + 1] = stereoData[i * 4 + 1]
        }
        return mono
    }

    private fun resample(data: ByteArray, fromRate: Int, toRate: Int, bitsPerSample: Int): ByteArray {
        if (fromRate == toRate || bitsPerSample != 16) return data
        val samplesIn = data.size / 2
        val samplesOut = (samplesIn.toLong() * toRate / fromRate).toInt()
        val out = ByteArray(samplesOut * 2)
        val ratio = samplesIn.toDouble() / samplesOut
        for (i in 0 until samplesOut) {
            val srcIdx = minOf((i * ratio).toInt(), samplesIn - 1)
            out[i * 2] = data[srcIdx * 2]
            out[i * 2 + 1] = data[srcIdx * 2 + 1]
        }
        return out
    }

    override fun stop() {
        isSpeaking = false
        try {
            currentAudioTrack?.stop()
            currentAudioTrack?.release()
        } catch (_: Exception) {}
        currentAudioTrack = null
        tts?.stop()
    }

    override fun release() {
        stop()
        tts?.shutdown()
        tts = null
        callback = null
    }

    companion object {
        private const val TAG = "AndroidTTSProvider"
    }
}
