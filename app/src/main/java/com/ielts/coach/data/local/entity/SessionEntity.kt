package com.ielts.coach.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ielts.coach.data.local.converter.Converters
import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart

@Entity(tableName = "sessions")
@TypeConverters(Converters::class)
data class SessionEntity(
    @PrimaryKey val sessionId: String,
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
)
