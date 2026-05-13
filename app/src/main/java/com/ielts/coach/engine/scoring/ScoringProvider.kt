package com.ielts.coach.engine.scoring

import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic

interface ScoringProvider {
    fun evaluate(request: ScoringRequest, callback: (ScoringResult) -> Unit)
}

data class ScoringRequest(
    val userTranscript: String,
    val examinerTranscript: String,
    val part: IELTSPart,
    val topic: IELTSTopic?,
    val durationSeconds: Int,
)

data class ScoringResult(
    val score: BandScore,
    val strengths: List<String>,
    val improvements: List<String>,
    val overallFeedback: String,
)
