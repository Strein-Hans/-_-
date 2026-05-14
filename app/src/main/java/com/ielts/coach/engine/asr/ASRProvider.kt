package com.ielts.coach.engine.asr

interface ASRProvider {

    interface ASRCallback {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(error: String)
    }

    fun startListening(callback: ASRCallback)
    fun stopListening()
    fun release()
    fun isAvailable(): Boolean
}
