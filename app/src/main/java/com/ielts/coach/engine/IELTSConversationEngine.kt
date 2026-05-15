package com.ielts.coach.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ielts.coach.data.model.*
import com.ielts.coach.engine.asr.ASRProvider
import com.ielts.coach.engine.conversation.ConversationProvider
import com.ielts.coach.engine.dh.DuixMobileManager
import com.ielts.coach.engine.scoring.ScoringProvider
import com.ielts.coach.engine.scoring.ScoringRequest
import com.ielts.coach.engine.scoring.ScoringResult
import com.ielts.coach.engine.tts.TTSProvider

class IELTSConversationEngine(
    private val asrProvider: ASRProvider,
    private val ttsProvider: TTSProvider,
    private val conversationProvider: ConversationProvider,
    private val scoringProvider: ScoringProvider,
    private val dhManager: DuixMobileManager? = null,
) {

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
        fun onFullMockTransition(nextPart: IELTSPart) {}
        fun onError(msg: String) {}
    }

    private var callback: ConversationCallback? = null
    private var currentSession: SpeakingSession? = null
    private var currentPart = IELTSPart.PART_1
    private var isRunning = false
    private var isFullMock = false
    private val mockSessions = mutableListOf<SpeakingSession>()
    private var mockTopic: IELTSTopic? = null
    private var mockPart1TurnCount = 0

    private val asrBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())

    private var prepRunnable: Runnable? = null
    private var monologueRunnable: Runnable? = null

    // Track examiner responses for transcript
    private val examinerTextBuffer = StringBuilder()

    fun setCallback(cb: ConversationCallback) {
        callback = cb
    }

    // ── Full Mock Test ────────────────────────────────────────────

    fun startFullMock(topic: IELTSTopic) {
        isFullMock = true
        mockTopic = topic
        mockSessions.clear()
        mockPart1TurnCount = 0
        callback?.onSessionStarted(IELTSPart.PART_1)
        startPart1()
    }

    private fun advanceFullMock() {
        if (!isFullMock) return
        when (currentPart) {
            IELTSPart.PART_1 -> {
                mockPart1TurnCount++
                if (mockPart1TurnCount >= 4) {
                    // Transition to Part 2
                    currentSession?.let { mockSessions.add(it) }
                    callback?.onFullMockTransition(IELTSPart.PART_2)
                    startPart2(mockTopic ?: return)
                }
            }
            IELTSPart.PART_2 -> {
                // Part 2 ends after monologue, transition handled by PracticeActivity
            }
            IELTSPart.PART_3 -> {
                // Part 3 scoring handled by endSessionAndScore
            }
        }
    }

    fun onPart2MonologueEnd() {
        if (!isFullMock) return
        currentSession?.let { mockSessions.add(it) }
        callback?.onFullMockTransition(IELTSPart.PART_3)
        startPart3(mockTopic ?: return)
    }

    // ── Part 1: Introduction & Interview ──────────────────────────

    fun startPart1() {
        startSession(IELTSPart.PART_1, null)
        // Kick off with first examiner question
        conversationProvider.getResponse("", IELTSPart.PART_1, null, emptyList()) { response ->
            handleExaminerResponse(response)
        }
    }

    // ── Part 2: Long Turn ─────────────────────────────────────────

    fun startPart2(topic: IELTSTopic) {
        startSession(IELTSPart.PART_2, topic)
    }

    fun startPreparation(durationSeconds: Int = 60) {
        val session = currentSession ?: return
        session.status = SessionStatus.PREPARING

        val topicText = session.topic?.formatTopicCard() ?: return
        speakExaminer("Here is your topic card. $topicText You have one minute to prepare. You can make notes if you wish.")

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

        speakExaminer("Now, please start speaking. You have up to two minutes.")

        var remaining = durationSeconds
        monologueRunnable = object : Runnable {
            override fun run() {
                if (remaining > 0) {
                    callback?.onMonologueTick(remaining)
                    remaining--
                    handler.postDelayed(this, 1000)
                } else {
                    callback?.onMonologueEnd()
                }
            }
        }
        handler.post(monologueRunnable!!)
    }

    // ── Part 3: Discussion ────────────────────────────────────────

    fun startPart3(topic: IELTSTopic) {
        startSession(IELTSPart.PART_3, topic)
        conversationProvider.getResponse("", IELTSPart.PART_3, topic, emptyList()) { response ->
            handleExaminerResponse(response)
        }
    }

    // ── Core: Handle ASR results ──────────────────────────────────

    private val asrCallback = object : ASRProvider.ASRCallback {
        override fun onPartialResult(text: String) {
            asrBuffer.append(text).append(" ")
            callback?.onUserAsrPartial(text)
        }

        override fun onFinalResult(text: String) {
            val fullText = if (asrBuffer.isNotBlank()) asrBuffer.toString().trim() else text
            asrBuffer.clear()
            if (fullText.isEmpty()) return

            Log.d(TAG, "User said: $fullText")
            callback?.onUserAsrFinal(fullText)
            currentSession?.userResponses?.add(UserResponse(fullText))

            // Get examiner response
            conversationProvider.getResponse(
                fullText,
                currentPart,
                currentSession?.topic,
                currentSession?.userResponses?.map { it.text } ?: emptyList(),
            ) { response ->
                handleExaminerResponse(response)
            }
        }

        override fun onError(error: String) {
            Log.e(TAG, "ASR error: $error")
            callback?.onError(error)
        }
    }

    fun onAsrPartial(text: String) {
        callback?.onUserAsrPartial(text)
    }

    fun onAsrFinal(text: String) {
        if (!isRunning) return
        val fullText = text.trim()
        if (fullText.isEmpty()) return

        Log.d(TAG, "User said: $fullText")
        callback?.onUserAsrFinal(fullText)
        currentSession?.userResponses?.add(UserResponse(fullText))

        conversationProvider.getResponse(
            fullText,
            currentPart,
            currentSession?.topic,
            currentSession?.userResponses?.map { it.text } ?: emptyList(),
        ) { response ->
            handleExaminerResponse(response)
            if (isFullMock) advanceFullMock()
        }
    }

    // ── Examiner speech ───────────────────────────────────────────

    private fun handleExaminerResponse(text: String) {
        currentSession?.examinerResponses?.add(text)
        callback?.onExaminerSpeaking(text)
        speakExaminer(text)
    }

    private fun speakExaminer(text: String) {
        dhManager?.startPush()
        ttsProvider.speak(text)
    }

    // ── Session lifecycle ─────────────────────────────────────────

    private fun startSession(part: IELTSPart, topic: IELTSTopic?) {
        stopSession()

        currentPart = part
        currentSession = SpeakingSession(part = part, topic = topic)
        isRunning = true
        asrBuffer.clear()

        // Start ASR listening
        asrProvider.startListening(asrCallback)

        callback?.onSessionStarted(part)
        Log.d(TAG, "Session started: $part")
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
        isFullMock = false
        asrBuffer.clear()
        asrProvider.stopListening()
        ttsProvider.stop()
        dhManager?.stopPush()
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
