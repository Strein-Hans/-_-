package com.ielts.coach.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ielts.coach.data.model.*
import com.ielts.coach.engine.asr.ASRProvider
import com.ielts.coach.engine.conversation.ConversationProvider
import com.ielts.coach.engine.conversation.ConversationResult
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
        fun onExaminerThinking() {}
        fun onEmotionDetected(emotion: String, confidence: Float) {}
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
    var isRunning = false
        private set
    private var isFullMock = false
    private val mockSessions = mutableListOf<SpeakingSession>()
    private var mockTopic: IELTSTopic? = null
    private var mockPart1TurnCount = 0

    private val asrBuffer = StringBuilder()
    private var latestPartialText = ""
    private var asrFinalized = false
    private val handler = Handler(Looper.getMainLooper())

    private var prepRunnable: Runnable? = null
    private var monologueRunnable: Runnable? = null
    private var asrSilenceRunnable: Runnable? = null
    private var asrMaxTimeoutRunnable: Runnable? = null
    private var thinkingRunnable: Runnable? = null

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
        Log.d(TAG, "startPart1: getting first question")
        val seed = buildRandomPart1Seed()
        // Send seed as a system-level topic hint, not as user speech
        conversationProvider.getResponse("[START] $seed", IELTSPart.PART_1, null, emptyList()) { result ->
            Log.d(TAG, "Got examiner response: ${result.text.take(50)}")
            handleExaminerResponse(result)
        }
    }

    private fun buildRandomPart1Seed(): String {
        val topics = listOf(
            "Let's talk about music. What kind of music do you enjoy?",
            "I'd like to ask you about food. What's your favorite type of cuisine?",
            "Let's discuss your hometown. What do you like most about it?",
            "I want to talk about travel. Where did you go on your last holiday?",
            "Let's talk about sports. Do you enjoy playing or watching any sports?",
            "I'd like to know about your daily routine. What's a typical day like for you?",
            "Let's discuss technology. How has technology changed the way you learn?",
            "I want to ask about reading. Do you enjoy reading books or articles?",
            "Let's talk about weather. What's your favorite season and why?",
            "I'd like to discuss festivals. How do you celebrate special occasions?",
            "Let's talk about nature. Do you prefer the mountains or the beach?",
            "I want to ask about shopping. Do you prefer shopping online or in stores?",
            "Let's discuss pets. Have you ever had a pet? Tell me about it.",
            "I'd like to talk about movies. What genre of films do you enjoy?",
            "Let's discuss transportation. How do you usually get to work or school?",
            "I want to ask about languages. Besides English, do you speak any other languages?",
            "Let's talk about hobbies. What do you enjoy doing in your free time?",
            "I'd like to discuss art. Are you interested in painting, photography, or design?",
            "Let's talk about social media. How do you use social platforms in your daily life?",
            "I want to ask about cooking. Can you cook? What's your signature dish?",
        )
        return topics.random()
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
        conversationProvider.getResponse("", IELTSPart.PART_3, topic, emptyList()) { result ->
            handleExaminerResponse(result)
        }
    }

    // ── Core: Handle ASR results ──────────────────────────────────

    private val asrCallback = object : ASRProvider.ASRCallback {
        override fun onPartialResult(text: String) {
            latestPartialText = text
            callback?.onUserAsrPartial(text)
            resetAsrSilenceTimer()
        }

        override fun onFinalResult(text: String) {
            cancelAsrTimers()
            if (asrFinalized) return
            asrFinalized = true
            val fullText = text.trim().ifBlank { latestPartialText.trim() }
            if (fullText.isEmpty()) return

            Log.d(TAG, "User said (final): $fullText")
            callback?.onUserAsrFinal(fullText)
            currentSession?.userResponses?.add(UserResponse(fullText))

            scheduleThinkingThenRespond(fullText)
        }

        override fun onError(error: String) {
            Log.e(TAG, "ASR error: $error")
            // If we have partial text, auto-finalize instead of showing error
            if (!asrFinalized && latestPartialText.isNotBlank()) {
                Log.d(TAG, "Auto-finalizing from partial on error")
                finalizeFromPartial()
            } else if (!asrFinalized) {
                callback?.onError(error)
            }
        }
    }

    private fun resetAsrSilenceTimer() {
        asrSilenceRunnable?.let { handler.removeCallbacks(it) }
        asrSilenceRunnable = Runnable {
            if (!asrFinalized && latestPartialText.isNotBlank()) {
                Log.d(TAG, "ASR silence timeout — auto-finalizing")
                finalizeFromPartial()
            }
        }
        handler.postDelayed(asrSilenceRunnable!!, 4000)
    }

    // Build conversation history: [examiner, user, examiner, user, ..., examiner]
    // Current user text is passed separately, so exclude the last user response.
    private fun buildConversationHistory(): List<String> {
        val history = mutableListOf<String>()
        val examResp = currentSession?.examinerResponses ?: return history
        val userResp = currentSession?.userResponses ?: return history
        val userCount = (userResp.size - 1).coerceAtLeast(0)

        for (i in examResp.indices) {
            history.add(examResp[i])
            if (i < userCount) {
                history.add(userResp[i].text)
            }
        }
        return history
    }

    private fun finalizeFromPartial() {
        cancelAsrTimers()
        if (asrFinalized) return
        asrFinalized = true
        val text = latestPartialText.trim()
        if (text.isEmpty()) return

        asrProvider.stopListening()
        Log.d(TAG, "User said (auto-final): $text")
        callback?.onUserAsrFinal(text)
        currentSession?.userResponses?.add(UserResponse(text))

        scheduleThinkingThenRespond(text)
    }

    private fun cancelAsrTimers() {
        asrSilenceRunnable?.let { handler.removeCallbacks(it) }
        asrSilenceRunnable = null
        asrMaxTimeoutRunnable?.let { handler.removeCallbacks(it) }
        asrMaxTimeoutRunnable = null
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

        scheduleThinkingThenRespond(fullText)
    }

    // ── Thinking delay for natural conversation ─────────────────────

    private fun scheduleThinkingThenRespond(userText: String) {
        callback?.onExaminerThinking()
        val delay = (200..400).random().toLong()
        thinkingRunnable = Runnable {
            thinkingRunnable = null
            conversationProvider.getResponse(
                userText,
                currentPart,
                currentSession?.topic,
                buildConversationHistory(),
            ) { result ->
                handleExaminerResponse(result)
                if (isFullMock) advanceFullMock()
            }
        }
        handler.postDelayed(thinkingRunnable!!, delay)
    }

    // ── Examiner speech ───────────────────────────────────────────

    private fun handleExaminerResponse(result: ConversationResult) {
        currentSession?.examinerResponses?.add(result.text)
        if (result.emotion != "neutral") {
            callback?.onEmotionDetected(result.emotion, result.emotionConfidence)
        }
        callback?.onExaminerSpeaking(result.text)
        speakExaminer(result.text)
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

        // Don't start ASR here — it will be started after TTS completes

        callback?.onSessionStarted(part)
        Log.d(TAG, "Session started: $part")
    }

    fun startAsrListening() {
        if (!isRunning) return
        cancelAsrTimers()
        latestPartialText = ""
        asrFinalized = false
        asrProvider.stopListening()
        asrProvider.startListening(asrCallback)

        // Max timeout: auto-finalize after 15s no matter what
        asrMaxTimeoutRunnable = Runnable {
            if (!asrFinalized && latestPartialText.isNotBlank()) {
                Log.d(TAG, "ASR max timeout — auto-finalizing")
                finalizeFromPartial()
            }
        }
        handler.postDelayed(asrMaxTimeoutRunnable!!, 15000)
    }

    fun stopSession() {
        prepRunnable?.let { handler.removeCallbacks(it) }
        monologueRunnable?.let { handler.removeCallbacks(it) }
        thinkingRunnable?.let { handler.removeCallbacks(it) }
        cancelAsrTimers()
        prepRunnable = null
        monologueRunnable = null
        thinkingRunnable = null

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
                    corrections = result.corrections,
                    vocabularySuggestions = result.vocabularySuggestions,
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
