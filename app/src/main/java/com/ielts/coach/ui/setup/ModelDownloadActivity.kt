package com.ielts.coach.ui.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.ielts.coach.R
import com.ielts.coach.databinding.ActivityModelDownloadBinding
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.home.HomeActivity
import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.VirtualModelUtil

class ModelDownloadActivity : BaseActivity() {

    private lateinit var binding: ActivityModelDownloadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val baseReady = VirtualModelUtil.checkBaseConfig(this)
        val avatarReady = VirtualModelUtil.checkModel(this, Constant.AVATAR_MODEL_URL)

        if (baseReady && avatarReady) {
            savePrefs(modelReady = true)
            goToHome()
            return
        }

        binding.btnRetry.setOnClickListener { startDownload(baseReady, avatarReady) }
        binding.btnSkip.setOnClickListener {
            savePrefs(modelReady = false)
            goToHome()
        }

        startDownload(baseReady, avatarReady)
    }

    private fun startDownload(baseReady: Boolean, avatarReady: Boolean) {
        binding.btnRetry.visibility = View.GONE

        if (!baseReady) {
            downloadBaseConfig()
        } else if (!avatarReady) {
            downloadAvatarModel()
        }
    }

    private fun downloadBaseConfig() {
        binding.tvStatus.text = getString(R.string.model_download_preparing)
        binding.tvProgress.text = "0%"
        binding.progressBar.progress = 0

        VirtualModelUtil.baseConfigDownload(this, object : VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String, current: Long, total: Long) {
                runOnUiThread {
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    binding.progressBar.progress = percent
                    binding.tvProgress.text = "$percent%"
                }
            }

            override fun onUnzipProgress(url: String, current: Long, total: Long) {
                runOnUiThread {
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    binding.progressBar.progress = percent
                    binding.tvProgress.text = "$percent%"
                }
            }

            override fun onDownloadComplete(url: String, dir: java.io.File) {
                runOnUiThread { downloadAvatarModel() }
            }

            override fun onDownloadFail(url: String, code: Int, msg: String) {
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.model_download_failed, "$code: $msg")
                    binding.btnRetry.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun downloadAvatarModel() {
        binding.tvStatus.text = getString(R.string.model_download_avatar)
        binding.tvProgress.text = "0%"
        binding.progressBar.progress = 0

        VirtualModelUtil.modelDownload(this, Constant.AVATAR_MODEL_URL, object : VirtualModelUtil.ModelDownloadCallback {
            override fun onDownloadProgress(url: String, current: Long, total: Long) {
                runOnUiThread {
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    binding.progressBar.progress = percent
                    binding.tvProgress.text = "$percent%"
                }
            }

            override fun onUnzipProgress(url: String, current: Long, total: Long) {
                runOnUiThread {
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    binding.progressBar.progress = percent
                    binding.tvProgress.text = "$percent%"
                }
            }

            override fun onDownloadComplete(url: String, dir: java.io.File) {
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.model_download_complete)
                    binding.progressBar.progress = 100
                    binding.tvProgress.text = "100%"
                    savePrefs(modelReady = true)
                    binding.btnSkip.postDelayed({ goToHome() }, 500)
                }
            }

            override fun onDownloadFail(url: String, code: Int, msg: String) {
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.model_download_failed, "$code: $msg")
                    binding.btnRetry.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun savePrefs(modelReady: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MODEL_READY, modelReady)
            .putString(KEY_MODEL_NAME, if (modelReady) Constant.AVATAR_MODEL_URL else "")
            .apply()
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_MODEL_READY = "model_ready"
        private const val KEY_MODEL_SKIPPED = "model_skipped"
        private const val KEY_MODEL_NAME = "model_name"
    }
}
