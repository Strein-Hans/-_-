package com.ielts.coach.engine.conversation

import android.os.Handler
import android.os.Looper
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic
import kotlin.random.Random

class TemplateConversationProvider : ConversationProvider {

    private val uiHandler = Handler(Looper.getMainLooper())
    private var turnIndex = 0
    private var part3QuestionIndex = 0

    override fun getResponse(
        userText: String,
        part: IELTSPart,
        topic: IELTSTopic?,
        history: List<String>,
        callback: (ConversationResult) -> Unit,
    ) {
        val response = when (part) {
            IELTSPart.PART_1 -> generatePart1Response(history.size)
            IELTSPart.PART_2 -> generatePart2Response(history.size)
            IELTSPart.PART_3 -> generatePart3Response(topic, history.size)
        }

        callback(ConversationResult(response))
    }

    private fun generatePart1Response(turnCount: Int): String {
        if (turnCount == 0) {
            return part1Openers.random()
        }
        return when (turnCount) {
            1 -> "Thank you. And what should I call you?"
            2 -> "Nice to meet you. Let's start with some questions. ${part1TopicStarters.random()}"
            else -> part1FollowUps.random()
        }
    }

    private fun generatePart2Response(turnCount: Int): String {
        return when (turnCount) {
            0 -> "Now, I'm going to give you a topic. I'd like you to talk about it for one to two minutes. You'll have one minute to think about what you're going to say."
            1 -> "Your preparation time starts now."
            2 -> "Thank you. Now, can you start speaking please? Remember you have up to two minutes."
            3 -> "Thank you. Can you tell me a bit more about that?"
            4 -> "That's the end of Part 2. Thank you."
            else -> "Very good. Let's move on to Part 3."
        }
    }

    private fun generatePart3Response(topic: IELTSTopic?, turnCount: Int): String {
        val questions = if (topic != null) {
            topic.followUpQuestions.ifEmpty { defaultPart3Questions }
        } else {
            defaultPart3Questions
        }

        val idx = turnCount.coerceAtMost(questions.size * 2)

        if (turnCount == 0) {
            return "We've been talking about ${topic?.topic ?: "this topic"}, and now I'd like to ask you some more general questions related to this. ${questions.getOrElse(0) { defaultPart3Questions[0] }}"
        }

        val followUps = listOf(
            "That's an interesting perspective. Can you elaborate on that?",
            "I see. Why do you think that is?",
            "Do you think this trend will continue in the future?",
            "How does this compare to the situation in your country?",
            "Some people might disagree with you. What would you say to them?",
            "Thank you. That concludes the speaking test.",
        )

        return followUps.getOrElse(idx) { "Thank you for your answer." }
    }

    fun reset() {
        turnIndex = 0
        part3QuestionIndex = 0
    }

    companion object {
        private val part1Openers = listOf(
            "Hello! Welcome to the IELTS Speaking test. My name is Sarah. Can you tell me your full name, please?",
            "Good afternoon! I'm Sarah, your examiner today. Could you tell me your name?",
            "Hi there! Welcome to the Speaking test. I'm Sarah. What's your full name?",
            "Hello! I'm Sarah and I'll be your examiner. May I have your name please?",
        )

        private val part1TopicStarters = listOf(
            "Do you work or are you a student?",
            "Let's talk about music. What kind of music do you enjoy?",
            "I'd like to ask about your daily routine. What's a typical day like?",
            "Do you enjoy cooking? What's your favorite dish?",
            "Let's talk about travel. Where did you go on your last holiday?",
            "What do you usually do in your free time?",
            "Do you like sports? What sports are popular where you live?",
            "Let's talk about films. What kind of movies do you enjoy watching?",
        )

        private val part1FollowUps = listOf(
            "That's interesting. Can you tell me more about that?",
            "I see. Why do you think that is?",
            "Has that always been the case, or has it changed over time?",
            "What do your friends or family think about that?",
            "Do you think that's common for people your age?",
            "If you could change one thing about that, what would it be?",
            "That's a great point. Let me ask you something else — what are your plans for the near future?",
            "Thank you for sharing. Let's move to a different topic. How important is technology in your daily life?",
            "Interesting. Now let's talk about your hometown — what do you like most about it?",
            "Thank you. That's the end of Part 1.",
        )

        private val defaultPart3Questions = listOf(
            "What are the advantages and disadvantages of this?",
            "How has this changed in recent years?",
            "Do you think this will be different in the future?",
            "What can governments do to improve this situation?",
            "How important is this for young people today?",
        )
    }
}
