package com.ielts.coach.engine.conversation

interface ConversationProvider {
    fun getResponse(
        userText: String,
        part: com.ielts.coach.data.model.IELTSPart,
        topic: com.ielts.coach.data.model.IELTSTopic?,
        history: List<String>,
        callback: (String) -> Unit,
    )
}
