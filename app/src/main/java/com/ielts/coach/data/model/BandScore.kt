package com.ielts.coach.data.model

data class BandScore(
    val fluencyCoherence: Float = 0f,
    val lexicalResource: Float = 0f,
    val grammaticalRangeAccuracy: Float = 0f,
    val pronunciation: Float = 0f,
) {
    val overall: Float
        get() {
            val scores = listOf(fluencyCoherence, lexicalResource, grammaticalRangeAccuracy, pronunciation)
                .filter { it > 0f }
            return if (scores.isNotEmpty()) scores.sum() / scores.size else 0f
        }

    fun toBandString(score: Float): String {
        if (score <= 0f) return "-"
        val rounded = (score * 2).toInt() / 2f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            String.format("%.1f", rounded)
        }
    }

    fun overallBandString(): String = toBandString(overall)
}
