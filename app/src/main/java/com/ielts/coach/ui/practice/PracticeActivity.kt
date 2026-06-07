package com.ielts.coach.ui.practice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.ielts.coach.R
import com.ielts.coach.data.model.*
import com.ielts.coach.data.repository.TopicRepository
import com.ielts.coach.databinding.ActivityPracticeBinding
import com.ielts.coach.engine.IELTSConversationEngine
import com.ielts.coach.engine.asr.ASRProvider
import com.ielts.coach.engine.asr.CloudASRProvider
import com.ielts.coach.engine.conversation.ConversationProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.ielts.coach.data.local.AppDatabase
import com.ielts.coach.data.local.entity.SessionEntity
import com.ielts.coach.engine.api.BackendApiClient
import com.ielts.coach.engine.conversation.BackendConversationProvider
import com.ielts.coach.engine.conversation.LLMConversationProvider
import com.ielts.coach.engine.conversation.TemplateConversationProvider
import com.ielts.coach.engine.dh.DuixMobileManager
import com.ielts.coach.engine.scoring.BackendScoringProvider
import com.ielts.coach.engine.scoring.LocalScoringProvider
import com.ielts.coach.engine.scoring.ScoringResult
import com.ielts.coach.engine.tts.AndroidTTSProvider
import com.ielts.coach.engine.tts.ServerTTSProvider
import com.ielts.coach.engine.tts.TTSProvider
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.report.ReportActivity

class PracticeActivity : BaseActivity() {

    private lateinit var binding: ActivityPracticeBinding

    private var dhManager: DuixMobileManager? = null
    private var asrProvider: ASRProvider? = null
    private var ttsProvider: TTSProvider? = null
    private var conversationEngine: IELTSConversationEngine? = null

    private var currentPart = IELTSPart.PART_1
    private var currentTopic: IELTSTopic? = null
    private var isFullMock = false
    private var fullMockPhase = 0
    private var voiceOnlyMode = false

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initEngines()
            setupUI()
            connectAndStart()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initEngines()
                setupUI()
                connectAndStart()
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用口语练习功能", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setupAudio() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

    private var sessionStarted = false

    private fun initEngines() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        voiceOnlyMode = !prefs.getBoolean(KEY_MODEL_READY, false)

        // Digital Human Manager (loads in background, doesn't block conversation start)
        val dh = DuixMobileManager(this)
        dhManager = dh
        if (!voiceOnlyMode) {
            val modelName = prefs.getString(KEY_MODEL_NAME, "") ?: ""
            if (modelName.isNotBlank()) {
                dh.setRenderView(binding.renderDigitalHuman)
                dh.init(modelName, object : DuixMobileManager.DHCallback {
                    override fun onReady() {
                        Log.d(TAG, "Digital human ready")
                        dh.triggerRandomMotion()
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "DH error: $error")
                        runOnUiThread { showError("Digital human: $error") }
                    }
                })
            } else {
                voiceOnlyMode = true
            }
        }

        if (voiceOnlyMode) {
            binding.renderDigitalHuman.visibility = View.GONE
        }

        // ASR Provider — iFlytek
        val iflytekAppId = prefs.getString(KEY_IFLYTEK_APP_ID, "05dd72b7").takeIf { !it.isNullOrBlank() } ?: "05dd72b7"
        val iflytekApiKey = prefs.getString(KEY_IFLYTEK_API_KEY, "dba1e0a679fb39dfc2a89f4eefe12420").takeIf { !it.isNullOrBlank() } ?: "dba1e0a679fb39dfc2a89f4eefe12420"
        val iflytekApiSecret = prefs.getString(KEY_IFLYTEK_API_SECRET, "MzBkZjViMTdmMGU1MjNlZDA3NjQ0ZTgz").takeIf { !it.isNullOrBlank() } ?: "MzBkZjViMTdmMGU1MjNlZDA3NjQ0ZTgz"
        val asr = CloudASRProvider(iflytekAppId, iflytekApiKey, iflytekApiSecret)
        asrProvider = asr

        // TTS Provider — Server TTS (backend mode) or Android TTS (local)
        val mode = prefs.getString(KEY_CONVERSATION_MODE, MODE_TEMPLATE) ?: MODE_TEMPLATE
        Log.d(TAG, "Conversation mode: $mode")
        val backendUrl = prefs.getString(KEY_BACKEND_URL, "http://8.136.188.53:8001") ?: "http://8.136.188.53:8001"
        val accent = prefs.getString(KEY_ACCENT, "british") ?: "british"
        val tts: TTSProvider = AndroidTTSProvider(this, playAudio = voiceOnlyMode)
        ttsProvider = tts
        tts.init(object : TTSProvider.TTSCallback {
            override fun onPCMData(pcmData: ByteArray) {
                if (!voiceOnlyMode) dh.pushPcm(pcmData)
            }

            override fun onSpeakStart() {
                Log.d(TAG, "TTS speaking")
            }

            override fun onSpeakComplete() {
                Log.d(TAG, "TTS complete — waiting 500ms before ASR")
                if (!voiceOnlyMode) {
                    dh.stopPush()
                    dh.triggerRandomMotion()
                }
                uiHandler.postDelayed({
                    conversationEngine?.startAsrListening()
                }, 500)
            }

            override fun onError(error: String) {
                Log.e(TAG, "TTS error: $error")
            }
        })

        // Conversation Provider — Template, LLM, or Backend
        val conversationProvider: ConversationProvider = when (mode) {
            MODE_BACKEND -> {
                BackendApiClient.baseUrl = backendUrl
                BackendConversationProvider()
            }
            else -> TemplateConversationProvider()
        }

        val useBackendScoring = mode == MODE_BACKEND
        val scoringProvider = if (useBackendScoring) {
            BackendScoringProvider()
        } else {
            LocalScoringProvider()
        }

        val engine = IELTSConversationEngine(
            asrProvider = asr,
            ttsProvider = tts,
            conversationProvider = conversationProvider,
            scoringProvider = scoringProvider,
            dhManager = if (voiceOnlyMode) null else dh,
        )
        conversationEngine = engine

        engine.setCallback(object : IELTSConversationEngine.ConversationCallback {
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
                runOnUiThread {
                    updateTimer(-1)
                    if (isFullMock) {
                        conversationEngine?.onPart2MonologueEnd()
                    }
                }
            }

            override fun onFullMockTransition(nextPart: IELTSPart) {
                runOnUiThread {
                    updatePartLabel(nextPart)
                    if (nextPart == IELTSPart.PART_2) {
                        val topic = currentTopic ?: TopicRepository(this@PracticeActivity).loadTopics().randomOrNull() ?: return@runOnUiThread
                        showTopicCard(topic)
                        conversationEngine?.startPreparation()
                    }
                    if (nextPart == IELTSPart.PART_3) {
                        binding.cardTopic.visibility = View.GONE
                    }
                }
            }

            override fun onScoringComplete(result: ScoringResult) {
                runOnUiThread { updateScoreBar(result.score) }
            }

            override fun onError(msg: String) {
                runOnUiThread { showError(msg) }
            }
        })
    }

    private fun setupUI() {
        binding.btnEndSession.setOnClickListener {
            finishSession()
        }
    }

    private fun connectAndStart() {
        if (sessionStarted) return
        sessionStarted = true
        val engine = conversationEngine ?: run {
            Log.e(TAG, "ENGINE IS NULL - cannot start")
            return
        }

        // Check ASR credentials before starting
        if (asrProvider != null && !asrProvider!!.isAvailable()) {
            Log.e(TAG, "ASR not available")
            showError(getString(R.string.asr_not_configured))
            return
        }

        Log.e(TAG, "connectAndStart: part=$currentPart, fullMock=$isFullMock, asrReady=${asrProvider?.isAvailable()}")
        if (isFullMock) {
            val topic = currentTopic ?: TopicRepository(this).loadTopics().randomOrNull() ?: return
            engine.startFullMock(topic)
        } else {
            when (currentPart) {
                IELTSPart.PART_1 -> engine.startPart1()
                IELTSPart.PART_2 -> {
                    val topic = currentTopic ?: TopicRepository(this).loadTopics().randomOrNull() ?: return
                    engine.startPart2(topic)
                    showTopicCard(topic)
                    engine.startPreparation()
                }
                IELTSPart.PART_3 -> {
                    val topic = currentTopic ?: TopicRepository(this).loadTopics().randomOrNull() ?: return
                    engine.startPart3(topic)
                }
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
        val engine = conversationEngine ?: run { finish(); return }
        isFinishing = true
        showScoringLoading()

        engine.endSessionAndScore { report ->
            runOnUiThread {
                if (report != null) {
                    // Save to Room
                    lifecycleScope.launch {
                        val entity = SessionEntity(
                            sessionId = report.sessionId,
                            part = report.part,
                            score = report.score,
                            userTranscript = report.userTranscript,
                            examinerTranscript = report.examinerTranscript,
                            wordCount = report.wordCount,
                            durationSeconds = report.durationSeconds,
                            strengths = report.strengths,
                            improvements = report.improvements,
                            corrections = report.corrections,
                            vocabularySuggestions = report.vocabularySuggestions,
                            overallFeedback = report.overallFeedback,
                            timestamp = report.timestamp,
                        )
                        AppDatabase.getInstance(this@PracticeActivity).sessionDao().insert(entity)
                    }
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
        conversationEngine?.stopSession()
        asrProvider?.release()
        ttsProvider?.release()
        dhManager?.release()
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val TAG = "PracticeActivity"
        const val EXTRA_PART = "extra_part"
        const val EXTRA_TOPIC_ID = "extra_topic_id"
        const val EXTRA_FULL_MOCK = "extra_full_mock"

        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_MODEL_READY = "model_ready"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_IFLYTEK_APP_ID = "iflytek_app_id"
        private const val KEY_IFLYTEK_API_KEY = "iflytek_api_key"
        private const val KEY_IFLYTEK_API_SECRET = "iflytek_api_secret"
        private const val KEY_CONVERSATION_MODE = "conversation_mode"
        private const val KEY_LLM_ENDPOINT = "llm_endpoint"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_ACCENT = "accent"

        private const val MODE_TEMPLATE = "template"
        private const val MODE_BACKEND = "backend"
        private const val MODE_LLM = "llm"
    }
}
