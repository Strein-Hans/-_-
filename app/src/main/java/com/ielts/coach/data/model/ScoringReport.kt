package com.ielts.coach.data.model

data class ScoringReport(
    val sessionId: String,
    val part: IELTSPart,
    val score: BandScore,
    val userTranscript: String,
    val examinerTranscript: String = "",
    val wordCount: Int,
    val durationSeconds: Int,
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val overallFeedback: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    val wordsPerMinute: Float
        get() = if (durationSeconds > 0) wordCount.toFloat() / durationSeconds * 60f else 0f
}
