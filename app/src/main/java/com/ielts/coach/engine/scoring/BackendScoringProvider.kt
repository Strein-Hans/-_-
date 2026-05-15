package com.ielts.coach.engine.scoring

import android.util.Log
import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic
import com.ielts.coach.engine.api.BackendApiClient
import org.json.JSONObject

class BackendScoringProvider : ScoringProvider {

    override fun evaluate(request: ScoringRequest, callback: (ScoringResult) -> Unit) {
        val body = JSONObject().apply {
            put("userTranscript", request.userTranscript)
            put("examinerTranscript", request.examinerTranscript)
            put("part", request.part.name)
            put("durationSeconds", request.durationSeconds)
            if (request.topic != null) {
                put("topic", JSONObject().apply {
                    put("id", request.topic.id)
                    put("category", request.topic.category)
                    put("topic", request.topic.topic)
                    put("difficulty", request.topic.difficulty)
                })
            }
        }

        BackendApiClient.post("/scoring", body) { result ->
            result.onSuccess { json ->
                try {
                    val scoreObj = json.getJSONObject("score")
                    val score = BandScore(
                        fluencyCoherence = scoreObj.optDouble("fluencyCoherence", 0.0).toFloat(),
                        lexicalResource = scoreObj.optDouble("lexicalResource", 0.0).toFloat(),
                        grammaticalRangeAccuracy = scoreObj.optDouble("grammaticalRangeAccuracy", 0.0).toFloat(),
                        pronunciation = scoreObj.optDouble("pronunciation", 0.0).toFloat(),
                    )
                    val strengths = json.optJSONArray("strengths")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    val improvements = json.optJSONArray("improvements")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    val feedback = json.optString("overallFeedback", "")
                    callback(ScoringResult(score, strengths, improvements, feedback))
                } catch (e: Exception) {
                    Log.e(TAG, "Parse scoring response failed", e)
                    fallbackLocal(request, callback)
                }
            }.onFailure { e ->
                Log.e(TAG, "Backend scoring failed", e)
                fallbackLocal(request, callback)
            }
        }
    }

    private fun fallbackLocal(request: ScoringRequest, callback: (ScoringResult) -> Unit) {
        LocalScoringProvider().evaluate(request, callback)
    }

    companion object {
        private const val TAG = "BackendScoring"
    }
}
