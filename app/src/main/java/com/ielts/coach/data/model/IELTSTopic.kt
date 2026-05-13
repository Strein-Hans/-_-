package com.ielts.coach.data.model

data class IELTSTopic(
    val id: String,
    val category: String,
    val topic: String,
    val bulletPoints: List<String>,
    val followUpQuestions: List<String>,
    val difficulty: Int = 3,
) {
    fun formatTopicCard(): String {
        val sb = StringBuilder()
        sb.appendLine(topic)
        sb.appendLine()
        sb.appendLine("You should say:")
        bulletPoints.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.append("and explain why this is significant to you.")
        return sb.toString()
    }
}
