package com.ielts.coach.engine

import android.content.Context
import android.util.Log
import ai.guiji.duix.sdk.cloud.Player
import ai.guiji.duix.sdk.cloud.VirtualFactory
import ai.guiji.duix.sdk.cloud.bean.DUIXBean
import org.webrtc.EglBase
import org.webrtc.VideoTrack

class DigitalHumanManager(
    private val context: Context,
    private val eglBaseContext: EglBase.Context,
) {

    interface DHCallback {
        fun onReady() {}
        fun onVideoTrackReady(track: VideoTrack) {}
        fun onAsrResult(text: String, sentenceEnd: Boolean) {}
        fun onDigitalHumanSpeaking(text: String) {}
        fun onDigitalHumanSpeakStart() {}
        fun onDigitalHumanSpeakStop() {}
        fun onSessionInfo(duixBean: DUIXBean?) {}
        fun onAudioSamples(audioFormat: Int, channelCount: Int, sampleRate: Int, data: ByteArray) {}
        fun onError(msgType: Int, msgSubType: Int, msg: String?) {}
    }

    var player: Player? = null
        private set

    private var callback: DHCallback? = null

    fun init(appId: String, appSecret: String) {
        VirtualFactory.init(appId, appSecret)
        player = VirtualFactory.getPlayer(context, eglBaseContext)
        player?.addCallback(object : Player.Callback {
            override fun onShow() {
                Log.d(TAG, "onShow")
            }

            override fun onReady() {
                Log.d(TAG, "onReady")
                callback?.onReady()
            }

            override fun onError(msgType: Int, msgSubType: Int, msg: String?) {
                Log.e(TAG, "onError: type=$msgType sub=$msgSubType msg=$msg")
                callback?.onError(msgType, msgSubType, msg)
            }

            override fun onAsrResult(text: String?, sentenceEnd: Boolean) {
                text?.let { callback?.onAsrResult(it, sentenceEnd) }
            }

            override fun onVideoTrack(track: VideoTrack) {
                callback?.onVideoTrackReady(track)
            }

            override fun onCameraTrack(track: VideoTrack) {
                Log.d(TAG, "onCameraTrack")
            }

            override fun onSpeakStart() {
                callback?.onDigitalHumanSpeakStart()
            }

            override fun onSpeakText(text: String?) {
                text?.let { callback?.onDigitalHumanSpeaking(it) }
            }

            override fun onSpeakStop() {
                callback?.onDigitalHumanSpeakStop()
            }

            override fun onTtsSpeakStart() {
                Log.d(TAG, "onTtsSpeakStart")
            }

            override fun onTtsSpeakText(text: String?) {
                Log.d(TAG, "onTtsSpeakText: $text")
            }

            override fun onTtsSpeakStop() {
                Log.d(TAG, "onTtsSpeakStop")
            }

            override fun onSessionInfo(duixBean: DUIXBean?) {
                callback?.onSessionInfo(duixBean)
            }
        })
    }

    fun addCallback(cb: DHCallback) {
        callback = cb
    }

    fun removeCallback() {
        callback = null
    }

    fun connect(conversationId: String) {
        player?.connect(conversationId)
    }

    fun askQuestion(text: String) {
        player?.speakWithQuestion(text, true)
    }

    fun speakText(text: String) {
        player?.speakWithTxt(text, true)
    }

    fun speakAudio(wavUrl: String) {
        player?.speakWithWav(wavUrl, true)
    }

    fun stopSpeaking() {
        player?.stopAudio()
    }

    fun setMicrophoneMute(mute: Boolean) {
        player?.setMicrophoneMute(mute)
    }

    fun setPromptVariables(json: String) {
        player?.setPromptVariables(json)
    }

    fun release() {
        player?.release()
        player = null
        callback = null
    }

    companion object {
        private const val TAG = "DigitalHumanManager"
    }
}
