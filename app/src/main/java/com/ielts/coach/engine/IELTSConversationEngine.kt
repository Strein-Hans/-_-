package com.ielts.coach.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ielts.coach.data.model.*
import com.ielts.coach.engine.scoring.ScoringProvider
import com.ielts.coach.engine.scoring.ScoringRequest
import com.ielts.coach.engine.scoring.ScoringResult
import org.json.JSONObject

class IELTSConversationEngine(
    private val dhManager: DigitalHumanManager,
    private val scoringProvider: ScoringProvider,
    private val conversationProvider: ConversationProvider = DUIXConversationProvider(dhManager),
) {

    interface ConversationProvider {
        fun getResponse(
            userText: String,
            part: IELTSPart,
            topic: IELTSTopic?,
            history: List<String>,
            callback: (String) -> Unit,
        )
    }

    class DUIXConversationProvider(private val dhManager: DigitalHumanManager) : ConversationProvider {
        override fun getResponse(
            userText: String,
            part: IELTSPart,
            topic: IELTSTopic?,
            history: List<String>,
            callback: (String) -> Unit,
        ) {
            dhManager.askQuestion(userText)
        }
    }

    interface ConversationCallback {
        fun onSessionStarted(part: IELTSPart) {}
        fun onUserAsrPartial(text: String) {}
        fun onUserAsrFinal(text: String) {}
        fun onExaminerSpeaking(text: String) {}
        fun onExaminerSpeakStop() {}
        fun onPreparationTick(remainingSeconds: Int) {}
        fun onPreparationEnd() {}
        fun onMonologueTick(remainingSeconds: Int) {}
        fun onMonologueEnd() {}
        fun onScoringComplete(result: ScoringResult) {}
        fun onSessionCompleted(report: ScoringReport) {}
        fun onError(msg: String) {}
    }

    private var callback: ConversationCallback? = null
    private var currentSession: SpeakingSession? = null
    private var currentPart = IELTSPart.PART_1
    private var currentConversationId: String = ""
    private var isRunning = false

    private val asrBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())

    private var prepRunnable: Runnable? = null
    private var monologueRunnable: Runnable? = null

    fun setCallback(cb: ConversationCallback) {
        callback = cb
    }

    // ── Part 1: Introduction & Interview ──────────────────────────

    fun startPart1(conversationId: String) {
        startSession(IELTSPart.PART_1, null, conversationId)
    }

    // ── Part 2: Long Turn ─────────────────────────────────────────

    fun startPart2(topic: IELTSTopic, conversationId: String) {
        startSession(IELTSPart.PART_2, topic, conversationId)
    }

    fun startPreparation(durationSeconds: Int = 60) {
        val session = currentSession ?: return
        session.status = SessionStatus.PREPARING

        val topicText = currentSession?.topic?.formatTopicCard() ?: return
        dhManager.speakText("Here is your topic card. $topicText You have one minute to prepare.")

        var remaining = durationSeconds
        prepRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    callback?.onPreparationTick(remaining)
                    remaining--
                    handler.postDelayed(this, 1000)
                } else {
                    callback?.onPreparationEnd()
                    startMonologue()
                }
            }
        }
        handler.post(prepRunnable!!)
    }

    fun startMonologue(durationSeconds: Int = 120) {
        val session = currentSession ?: return
        session.status = SessionStatus.IN_PROGRESS
        callback?.onSessionStarted(IELTSPart.PART_2)
        dhManager.setMicrophoneMute(false)

        var remaining = durationSeconds
        monologueRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    callback?.onMonologueTick(remaining)
                    remaining--
                    handler.postDelayed(this, 1000)
                } else {
                    callback?.onMonologueEnd()
                    conversationProvider.getResponse(
                        "Thank you. Now, let's move on to discuss this topic further.",
                        IELTSPart.PART_3,
                        currentSession?.topic,
                        emptyList()
                    ) { _ -> }
                }
            }
        }
        handler.post(monologueRunnable!!)
    }

    // ── Part 3: Discussion ────────────────────────────────────────

    fun startPart3(topic: IELTSTopic, conversationId: String) {
        startSession(IELTSPart.PART_3, topic, conversationId)
    }

    // ── Examiner speech capture ───────────────────────────────────

    fun onExaminerSpeaking(text: String) {
        currentSession?.examinerResponses?.add(text)
        callback?.onExaminerSpeaking(text)
    }

    // ── Core: Handle ASR results ──────────────────────────────────

    fun onAsrResult(text: String, sentenceEnd: Boolean) {
        if (!isRunning) return

        if (sentenceEnd) {
            val fullText = asrBuffer.toString().trim()
            asrBuffer.clear()
            if (fullText.isEmpty()) return

            Log.d(TAG, "User said: $fullText")
            callback?.onUserAsrFinal(fullText)

            currentSession?.userResponses?.add(UserResponse(fullText))

            conversationProvider.getResponse(
                fullText, currentPart, currentSession?.topic,
                currentSession?.userResponses?.map { it.text } ?: emptyList()
            ) { _ -> }
        } else {
            asrBuffer.append(text).append(" ")
            callback?.onUserAsrPartial(text)
        }
    }

    // ── Session lifecycle ─────────────────────────────────────────

    private fun startSession(part: IELTSPart, topic: IELTSTopic?, conversationId: String) {
        stopSession()

        currentPart = part
        currentConversationId = conversationId
        currentSession = SpeakingSession(part = part, topic = topic)
        isRunning = true
        asrBuffer.clear()

        injectIELTSPrompt(part, topic)

        dhManager.connect(conversationId)
        callback?.onSessionStarted(part)

        Log.d(TAG, "Session started: $part")
    }

    private fun injectIELTSPrompt(part: IELTSPart, topic: IELTSTopic?) {
        val json = JSONObject().apply {
            put("role", "ielts_examiner")
            put("part", part.name)
            topic?.let {
                put("topic", it.topic)
                put("category", it.category)
                put("bullet_points", org.json.JSONArray(it.bulletPoints))
            }
        }
        dhManager.setPromptVariables(json.toString())
    }

    fun stopSession() {
        prepRunnable?.let { handler.removeCallbacks(it) }
        monologueRunnable?.let { handler.removeCallbacks(it) }
        prepRunnable = null
        monologueRunnable = null

        currentSession?.let {
            it.status = SessionStatus.COMPLETED
            it.endTime = System.currentTimeMillis()
        }

        isRunning = false
        asrBuffer.clear()
    }

    fun endSessionAndScore(callback: (ScoringReport?) -> Unit) {
        stopSession()
        val session = currentSession ?: run { callback(null); return }

        val transcript = session.userResponses.joinToString(" ") { it.text }
        val examinerTranscript = session.examinerResponses.joinToString(" ")
        val wordCount = transcript.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val durationSeconds = ((session.endTime - session.startTime) / 1000).toInt().coerceAtLeast(1)

        if (transcript.isBlank()) {
            val report = ScoringReport(
                sessionId = session.id,
                part = session.part,
                score = BandScore(),
                userTranscript = transcript,
                examinerTranscript = examinerTranscript,
                wordCount = wordCount,
                durationSeconds = durationSeconds,
            )
            callback(report)
            return
        }

        val request = ScoringRequest(
            userTranscript = transcript,
            examinerTranscript = examinerTranscript,
            part = session.part,
            topic = session.topic,
            durationSeconds = durationSeconds,
        )

        scoringProvider.evaluate(request) { result ->
            handler.post {
                this.callback?.onScoringComplete(result)
                val report = ScoringReport(
                    sessionId = session.id,
                    part = session.part,
                    score = result.score,
                    userTranscript = transcript,
                    examinerTranscript = examinerTranscript,
                    wordCount = wordCount,
                    durationSeconds = durationSeconds,
                    strengths = result.strengths,
                    improvements = result.improvements,
                    overallFeedback = result.overallFeedback,
                )
                callback(report)
            }
        }
    }

    fun getCurrentSession(): SpeakingSession? = currentSession

    companion object {
        private const val TAG = "IELTSConversationEngine"
    }
}
