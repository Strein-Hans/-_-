package com.ielts.coach.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ielts.coach.R
import com.ielts.coach.data.model.Accent
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.repository.TopicRepository
import com.ielts.coach.databinding.ActivityHomeBinding
import com.ielts.coach.engine.dh.DuixMobileManager
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.practice.PracticeActivity
import com.ielts.coach.ui.settings.SettingsActivity
import com.ielts.coach.ui.setup.ModelDownloadActivity

class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if model download is needed (first launch)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modelReady = prefs.getBoolean(KEY_MODEL_READY, false)
        val modelSkipped = prefs.getBoolean(KEY_MODEL_SKIPPED, false)

        if (!modelReady && !modelSkipped) {
            startActivity(Intent(this, ModelDownloadActivity::class.java))
            finish()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.cardPart1.setOnClickListener {
            checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_PART1)
        }

        binding.cardPart2.setOnClickListener {
            checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_PART2)
        }

        binding.cardPart3.setOnClickListener {
            checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_PART3)
        }

        binding.cardFullMock.setOnClickListener {
            checkAndRequestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RC_FULL_MOCK)
        }

        binding.tvSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tvHistory.setOnClickListener {
            // TODO: HistoryActivity
        }
    }

    override fun onPermissionsResult(granted: Boolean, code: Int) {
        if (!granted) return

        val intent = Intent(this, PracticeActivity::class.java)
        when (code) {
            RC_PART1 -> {
                intent.putExtra(PracticeActivity.EXTRA_PART, IELTSPart.PART_1.name)
            }
            RC_PART2 -> {
                val topic = TopicRepository(this).loadTopics().randomOrNull()
                intent.putExtra(PracticeActivity.EXTRA_PART, IELTSPart.PART_2.name)
                intent.putExtra(PracticeActivity.EXTRA_TOPIC_ID, topic?.id ?: "")
            }
            RC_PART3 -> {
                val topic = TopicRepository(this).loadTopics().randomOrNull()
                intent.putExtra(PracticeActivity.EXTRA_PART, IELTSPart.PART_3.name)
                intent.putExtra(PracticeActivity.EXTRA_TOPIC_ID, topic?.id ?: "")
            }
            RC_FULL_MOCK -> {
                intent.putExtra(PracticeActivity.EXTRA_PART, IELTSPart.PART_1.name)
                intent.putExtra(PracticeActivity.EXTRA_FULL_MOCK, true)
            }
        }
        startActivity(intent)
    }

    companion object {
        private const val RC_PART1 = 101
        private const val RC_PART2 = 102
        private const val RC_PART3 = 103
        private const val RC_FULL_MOCK = 104

        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_MODEL_READY = "model_ready"
        private const val KEY_MODEL_SKIPPED = "model_skipped"
    }
}
