package com.ielts.coach.engine.tts

interface TTSProvider {

    interface TTSCallback {
        fun onPCMData(pcmData: ByteArray)
        fun onSpeakStart()
        fun onSpeakComplete()
        fun onError(error: String)
    }

    fun init(callback: TTSCallback)
    fun speak(text: String)
    fun stop()
    fun release()
}
