package com.ielts.coach.engine.dh

import android.content.Context
import android.util.Log
import ai.guiji.duix.sdk.client.Callback
import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.VirtualModelUtil
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.sdk.client.render.DUIXTextureView

class DuixMobileManager(
    private val context: Context,
) {

    interface DHCallback {
        fun onReady() {}
        fun onError(error: String) {}
    }

    private var duix: DUIX? = null
    private var callback: DHCallback? = null
    private var isReady = false
    private var renderer: DUIXRenderer? = null
    private var textureView: DUIXTextureView? = null

    fun setRenderView(view: DUIXTextureView) {
        textureView = view
    }

    fun init(modelName: String, callback: DHCallback) {
        this.callback = callback

        if (!VirtualModelUtil.checkBaseConfig(context)) {
            callback.onError("Base config not downloaded. Need gj_dh_res.")
            return
        }

        if (!VirtualModelUtil.checkModel(context, modelName)) {
            callback.onError("Model not downloaded: $modelName")
            return
        }

        val glView = textureView
        if (glView == null) {
            callback.onError("DUIXTextureView not set")
            return
        }

        val ren = DUIXRenderer(context, glView)
        renderer = ren

        glView.setEGLContextClientVersion(2)
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glView.isOpaque = false
        glView.setRenderer(ren)
        glView.renderMode = DUIXTextureView.RENDERMODE_WHEN_DIRTY

        duix = DUIX(context, modelName, ren, object : Callback {
            override fun onEvent(event: String, msg: String, info: Any?) {
                when (event) {
                    Constant.CALLBACK_EVENT_INIT_READY -> {
                        isReady = true
                        Log.d(TAG, "Duix.Mobile ready")
                        this@DuixMobileManager.callback?.onReady()
                    }
                    Constant.CALLBACK_EVENT_INIT_ERROR -> {
                        Log.e(TAG, "Duix.Mobile init error: $msg")
                        this@DuixMobileManager.callback?.onError(msg)
                    }
                    Constant.CALLBACK_EVENT_MOTION_END -> {
                        duix?.startRandomMotion(false)
                    }
                }
            }
        })

        duix?.init()
    }

    fun pushPcm(pcmData: ByteArray) {
        if (!isReady) return
        duix?.pushPcm(pcmData)
    }

    fun startPush() {
        duix?.startPush()
    }

    fun stopPush() {
        duix?.stopPush()
    }

    fun triggerRandomMotion() {
        duix?.startRandomMotion(false)
    }

    fun release() {
        duix?.release()
        duix = null
        renderer = null
        callback = null
        isReady = false
    }

    fun isReady(): Boolean = isReady

    companion object {
        private const val TAG = "DuixMobileManager"
    }
}
