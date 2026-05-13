package com.ielts.coach.data.model

import java.util.UUID

enum class IELTSPart {
    PART_1,
    PART_2,
    PART_3,
}

enum class SessionStatus {
    PREPARING,
    IN_PROGRESS,
    COMPLETED,
    ABORTED,
}

data class SpeakingSession(
    val id: String = UUID.randomUUID().toString(),
    val part: IELTSPart,
    val topic: IELTSTopic? = null,
    val accent: Accent = Accent.BRITISH,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long = 0L,
    var status: SessionStatus = SessionStatus.PREPARING,
    val userResponses: MutableList<UserResponse> = mutableListOf(),
    val examinerResponses: MutableList<String> = mutableListOf(),
    var score: BandScore? = null,
)

data class UserResponse(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String? = null,
)

enum class Accent(val display: String, val code: String) {
    BRITISH("British", "british"),
    AMERICAN("American", "american"),
}
