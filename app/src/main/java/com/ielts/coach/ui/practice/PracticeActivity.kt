package com.ielts.coach.ui.practice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.google.gson.Gson
import com.ielts.coach.R
import com.ielts.coach.data.model.*
import com.ielts.coach.data.repository.TopicRepository
import com.ielts.coach.databinding.ActivityPracticeBinding
import com.ielts.coach.engine.DigitalHumanManager
import com.ielts.coach.engine.IELTSConversationEngine
import com.ielts.coach.engine.scoring.LocalScoringProvider
import com.ielts.coach.engine.scoring.ScoringResult
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.report.ReportActivity
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

class PracticeActivity : BaseActivity() {

    private lateinit var binding: ActivityPracticeBinding

    private val eglBase = EglBase.create()
    private val eglBaseContext = eglBase.eglBaseContext

    private lateinit var dhManager: DigitalHumanManager
    private lateinit var conversationEngine: IELTSConversationEngine

    private var currentPart = IELTSPart.PART_1
    private var currentTopic: IELTSTopic? = null
    private var isFullMock = false
    private var fullMockPhase = 0

    private val uiHandler = Handler(Looper.getMainLooper())
    private var asrHideRunnable: Runnable? = null
    private var isFinishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()

        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAudio()
        parseIntent()
        initEngines()
        setupRenderer()
        setupUI()
        connectAndStart()
    }

    private fun setupAudio() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    private fun parseIntent() {
        currentPart = IELTSPart.valueOf(
            intent.getStringExtra(EXTRA_PART) ?: IELTSPart.PART_1.name
        )
        isFullMock = intent.getBooleanExtra(EXTRA_FULL_MOCK, false)

        val topicId = intent.getStringExtra(EXTRA_TOPIC_ID)
        if (topicId != null) {
            currentTopic = TopicRepository(this).loadTopics().find { it.id == topicId }
        }
    }

    private fun initEngines() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appId = prefs.getString(KEY_APP_ID, "") ?: ""
        val appKey = prefs.getString(KEY_APP_KEY, "") ?: ""
        val conversationId = prefs.getString(KEY_CONVERSATION_ID, "") ?: ""

        if (appId.isBlank() || appKey.isBlank() || conversationId.isBlank()) {
            showError("Please configure DUIX credentials in Settings first.")
            return
        }

        val scoringProvider = LocalScoringProvider()

        dhManager = DigitalHumanManager(this, eglBaseContext)
        dhManager.init(appId, appKey)
        dhManager.addCallback(object : DigitalHumanManager.DHCallback {
            override fun onReady() {
                Log.d(TAG, "Digital human ready")
            }

            override fun onVideoTrackReady(track: VideoTrack) {
                runOnUiThread { track.addSink(binding.renderDigitalHuman) }
            }

            override fun onAsrResult(text: String, sentenceEnd: Boolean) {
                conversationEngine.onAsrResult(text, sentenceEnd)
            }

            override fun onDigitalHumanSpeaking(text: String) {
                conversationEngine.onExaminerSpeaking(text)
            }

            override fun onDigitalHumanSpeakStop() {
                Log.d(TAG, "Examiner stopped speaking")
            }

            override fun onError(msgType: Int, msgSubType: Int, msg: String?) {
                runOnUiThread { showError("Error $msgType: $msg") }
            }
        })

        conversationEngine = IELTSConversationEngine(dhManager, scoringProvider)
        conversationEngine.setCallback(object : IELTSConversationEngine.ConversationCallback {
            override fun onSessionStarted(part: IELTSPart) {
                runOnUiThread { updatePartLabel(part) }
            }

            override fun onUserAsrPartial(text: String) {
                runOnUiThread { showAsrPartial(text) }
            }

            override fun onUserAsrFinal(text: String) {
                runOnUiThread { showAsrFinal(text) }
            }

            override fun onExaminerSpeaking(text: String) {
                Log.d(TAG, "Examiner: $text")
            }

            override fun onPreparationTick(remainingSeconds: Int) {
                runOnUiThread { updateTimer(remainingSeconds) }
            }

            override fun onPreparationEnd() {
                runOnUiThread {
                    binding.cardTopic.visibility = View.GONE
                    updateTimer(-1)
                }
            }

            override fun onMonologueTick(remainingSeconds: Int) {
                runOnUiThread { updateTimer(remainingSeconds) }
            }

            override fun onMonologueEnd() {
                runOnUiThread { updateTimer(-1) }
            }

            override fun onScoringComplete(result: ScoringResult) {
                runOnUiThread { updateScoreBar(result.score) }
            }
        })
    }

    private fun setupRenderer() {
        binding.renderDigitalHuman.init(eglBaseContext, null)
        binding.renderDigitalHuman.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.renderDigitalHuman.setMirror(false)
        binding.renderDigitalHuman.setEnableHardwareScaler(false)
    }

    private fun setupUI() {
        binding.btnEndSession.setOnClickListener {
            finishSession()
        }
    }

    private fun connectAndStart() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val conversationId = prefs.getString(KEY_CONVERSATION_ID, "") ?: ""
        if (conversationId.isBlank()) return

        when (currentPart) {
            IELTSPart.PART_1 -> conversationEngine.startPart1(conversationId)
            IELTSPart.PART_2 -> {
                val topic = currentTopic ?: TopicRepository(this).loadTopics().randomOrNull() ?: return
                conversationEngine.startPart2(topic, conversationId)
                showTopicCard(topic)
                conversationEngine.startPreparation()
            }
            IELTSPart.PART_3 -> {
                val topic = currentTopic ?: TopicRepository(this).loadTopics().randomOrNull() ?: return
                conversationEngine.startPart3(topic, conversationId)
            }
        }
    }

    // ── UI Updates ────────────────────────────────────────────────

    private fun updatePartLabel(part: IELTSPart) {
        val label = when (part) {
            IELTSPart.PART_1 -> getString(R.string.part1_title)
            IELTSPart.PART_2 -> getString(R.string.part2_title)
            IELTSPart.PART_3 -> getString(R.string.part3_title)
        }
        binding.tvPartLabel.text = label
    }

    private fun showTopicCard(topic: IELTSTopic) {
        binding.cardTopic.visibility = View.VISIBLE
        binding.tvTopicContent.text = topic.formatTopicCard()
    }

    private fun updateTimer(seconds: Int) {
        if (seconds < 0) {
            binding.tvTimer.text = ""
            return
        }
        val min = seconds / 60
        val sec = seconds % 60
        binding.tvTimer.text = String.format("%d:%02d", min, sec)
    }

    private fun showAsrPartial(text: String) {
        asrHideRunnable?.let { uiHandler.removeCallbacks(it) }
        binding.tvAsrResult.text = text
        binding.tvAsrResult.visibility = View.VISIBLE
    }

    private fun showAsrFinal(text: String) {
        binding.tvAsrResult.text = text
        asrHideRunnable = Runnable {
            binding.tvAsrResult.visibility = View.INVISIBLE
        }
        uiHandler.postDelayed(asrHideRunnable!!, 2000)
    }

    private fun updateScoreBar(score: BandScore) {
        binding.scoreBar.visibility = View.VISIBLE
        binding.tvScoreFC.text = "FC: ${score.toBandString(score.fluencyCoherence)}"
        binding.tvScoreLR.text = "LR: ${score.toBandString(score.lexicalResource)}"
        binding.tvScoreGRA.text = "GRA: ${score.toBandString(score.grammaticalRangeAccuracy)}"
        binding.tvScorePR.text = "PR: ${score.toBandString(score.pronunciation)}"
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun showScoringLoading() {
        binding.tvScoringStatus.text = getString(R.string.scoring_in_progress)
        binding.tvScoringStatus.visibility = View.VISIBLE
        binding.btnEndSession.isEnabled = false
    }

    // ── Session End ───────────────────────────────────────────────

    private fun finishSession() {
        if (isFinishing) return
        isFinishing = true
        showScoringLoading()

        conversationEngine.endSessionAndScore { report ->
            runOnUiThread {
                if (report != null) {
                    val intent = Intent(this, ReportActivity::class.java)
                    intent.putExtra(ReportActivity.EXTRA_REPORT, Gson().toJson(report))
                    startActivity(intent)
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationEngine.stopSession()
        dhManager.release()
        binding.renderDigitalHuman.release()
        eglBase.release()
    }

    companion object {
        private const val TAG = "PracticeActivity"
        const val EXTRA_PART = "extra_part"
        const val EXTRA_TOPIC_ID = "extra_topic_id"
        const val EXTRA_FULL_MOCK = "extra_full_mock"
        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_CONVERSATION_ID = "conversation_id"
    }
}
