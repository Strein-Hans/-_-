package com.ielts.coach.engine.conversation

data class ConversationResult(
    val text: String,
    val emotion: String = "neutral",
    val emotionConfidence: Float = 0f,
)

interface ConversationProvider {
    fun getResponse(
        userText: String,
        part: com.ielts.coach.data.model.IELTSPart,
        topic: com.ielts.coach.data.model.IELTSTopic?,
        history: List<String>,
        callback: (ConversationResult) -> Unit,
    )
}
