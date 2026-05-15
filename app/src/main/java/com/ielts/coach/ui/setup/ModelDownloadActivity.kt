package com.ielts.coach.ui.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.ielts.coach.R
import com.ielts.coach.databinding.ActivityModelDownloadBinding
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.home.HomeActivity
import ai.guiji.duix.sdk.client.VirtualModelUtil

class ModelDownloadActivity : BaseActivity() {

    private lateinit var binding: ActivityModelDownloadBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If base config and model already downloaded, skip to home
        if (VirtualModelUtil.checkBaseConfig(this)) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MODEL_READY, true)
                .apply()
            goToHome()
            return
        }

        binding.btnRetry.setOnClickListener { startDownload() }
        binding.btnSkip.setOnClickListener {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MODEL_SKIPPED, true)
                .apply()
            goToHome()
        }

        startDownload()
    }

    private fun startDownload() {
        binding.tvStatus.text = getString(R.string.model_download_preparing)
        binding.tvProgress.text = "0%"
        binding.progressBar.progress = 0
        binding.btnRetry.visibility = View.GONE

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
                runOnUiThread {
                    binding.tvStatus.text = getString(R.string.model_download_complete)
                    binding.progressBar.progress = 100
                    binding.tvProgress.text = "100%"
                    binding.btnRetry.visibility = View.GONE

                    // Save model ready state
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_MODEL_READY, true)
                        .apply()

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

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_MODEL_READY = "model_ready"
        private const val KEY_MODEL_SKIPPED = "model_skipped"
    }
}
