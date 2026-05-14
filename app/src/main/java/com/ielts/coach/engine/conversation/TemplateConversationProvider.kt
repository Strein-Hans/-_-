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
        callback: (String) -> Unit,
    ) {
        val response = when (part) {
            IELTSPart.PART_1 -> generatePart1Response(history.size)
            IELTSPart.PART_2 -> generatePart2Response(history.size)
            IELTSPart.PART_3 -> generatePart3Response(topic, history.size)
        }

        // Simulate examiner "thinking" delay
        val delay = Random.nextLong(800, 1500)
        uiHandler.postDelayed({
            callback(response)
        }, delay)
    }

    private fun generatePart1Response(turnCount: Int): String {
        return when (turnCount) {
            0 -> "Hello! Welcome to the IELTS Speaking test. My name is Sarah. Can you tell me your full name, please?"
            1 -> "Thank you. And what should I call you?"
            2 -> "Nice to meet you. Let's start with some questions about yourself. Do you work or are you a student?"
            3 -> "What do you enjoy most about your studies or work?"
            4 -> "That's interesting. Do you like reading? What kind of books do you enjoy?"
            5 -> "Let's talk about your hometown. What's it like?"
            6 -> "What do you like most about your hometown?"
            7 -> "Would you say it's a good place for young people to live?"
            8 -> "Let's move on to talk about music. What kind of music do you listen to?"
            9 -> "Do you play any musical instruments?"
            10 -> "Has your taste in music changed over the years?"
            11 -> "Thank you. That's the end of Part 1."
            else -> "Thank you for sharing that. Let me ask you one more thing — what are your plans for the near future?"
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
        private val defaultPart3Questions = listOf(
            "What are the advantages and disadvantages of this?",
            "How has this changed in recent years?",
            "Do you think this will be different in the future?",
            "What can governments do to improve this situation?",
            "How important is this for young people today?",
        )
    }
}
