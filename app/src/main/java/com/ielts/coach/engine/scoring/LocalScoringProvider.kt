package com.ielts.coach.engine.scoring

import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.IELTSTopic

class LocalScoringProvider : ScoringProvider {

    override fun evaluate(request: ScoringRequest, callback: (ScoringResult) -> Unit) {
        Thread {
            val result = doEvaluate(request)
            callback(result)
        }.start()
    }

    private fun doEvaluate(request: ScoringRequest): ScoringResult {
        val words = tokenize(request.userTranscript)
        if (words.isEmpty()) {
            return ScoringResult(
                score = BandScore(),
                strengths = emptyList(),
                improvements = listOf("No speech detected. Try to speak more next time."),
                overallFeedback = "No speech was detected during this session.",
            )
        }

        val fc = scoreFluencyCoherence(words, request.userTranscript, request.part)
        val lr = scoreLexicalResource(words, request.userTranscript)
        val gra = scoreGrammaticalRangeAccuracy(words, request.userTranscript)

        val score = BandScore(
            fluencyCoherence = fc,
            lexicalResource = lr,
            grammaticalRangeAccuracy = gra,
            pronunciation = 0f,
        )

        val strengths = buildStrengths(score, words, request.userTranscript)
        val improvements = buildImprovements(score, words, request.userTranscript, request.part)
        val feedback = buildOverallFeedback(score, request.part, words.size)

        return ScoringResult(score, strengths, improvements, feedback)
    }

    // ── FC ─────────────────────────────────────────────────────────

    private fun scoreFluencyCoherence(words: List<String>, text: String, part: IELTSPart): Float {
        var score = 5.0f
        val wordCount = words.size
        score += when (part) {
            IELTSPart.PART_1 -> when {
                wordCount in 15..50 -> 1.0f
                wordCount in 8..14 -> 0.5f
                wordCount > 50 -> 0.5f
                else -> -0.5f
            }
            IELTSPart.PART_2 -> when {
                wordCount in 100..300 -> 1.5f
                wordCount in 50..99 -> 1.0f
                wordCount in 30..49 -> 0.5f
                else -> -0.5f
            }
            IELTSPart.PART_3 -> when {
                wordCount in 30..80 -> 1.0f
                wordCount in 15..29 -> 0.5f
                wordCount > 80 -> 0.5f
                else -> -0.5f
            }
        }

        val connectiveHits = CONNECTIVES.count { text.lowercase().contains(it) }
        score += when {
            connectiveHits >= 3 -> 1.0f
            connectiveHits >= 2 -> 0.5f
            connectiveHits >= 1 -> 0.25f
            else -> 0f
        }

        val fillerHits = FILLERS.count { text.lowercase().contains(it) }
        score -= fillerHits.coerceAtMost(3) * 0.25f

        return score.coerceIn(1.0f, 9.0f)
    }

    // ── LR ─────────────────────────────────────────────────────────

    private fun scoreLexicalResource(words: List<String>, text: String): Float {
        if (words.isEmpty()) return 1.0f
        var score = 5.0f

        val uniqueWords = words.map { it.lowercase() }.toSet()
        val ttr = uniqueWords.size.toFloat() / words.size.toFloat()
        score += when {
            ttr >= 0.8 -> 1.5f
            ttr >= 0.65 -> 1.0f
            ttr >= 0.5 -> 0.5f
            else -> -0.5f
        }

        val advancedCount = ADVANCED_VOCAB.count { text.lowercase().contains(it) }
        score += when {
            advancedCount >= 4 -> 1.5f
            advancedCount >= 2 -> 1.0f
            advancedCount >= 1 -> 0.5f
            else -> 0f
        }

        val avgLen = words.sumOf { it.length }.toFloat() / words.size
        score += when {
            avgLen >= 5.5 -> 0.5f
            avgLen >= 4.5 -> 0.25f
            else -> 0f
        }

        return score.coerceIn(1.0f, 9.0f)
    }

    // ── GRA ────────────────────────────────────────────────────────

    private fun scoreGrammaticalRangeAccuracy(words: List<String>, text: String): Float {
        if (words.isEmpty()) return 1.0f
        var score = 5.0f

        val subordinatePatterns = listOf(
            "which ", "that ", "although ", "because ", "while ", "whereas ",
            "if ", "when ", "unless ", "since ", "however ", "therefore ",
            "consequently ", "nevertheless ", "furthermore ", "moreover ",
        )
        val complexHits = subordinatePatterns.count { text.lowercase().contains(it) }
        score += when {
            complexHits >= 4 -> 1.5f
            complexHits >= 2 -> 1.0f
            complexHits >= 1 -> 0.5f
            else -> -0.5f
        }

        val errorPatterns = listOf(
            Regex("""\b(he|she|it)\s+(are|were)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(I)\s+(is|was)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(they)\s+(is|was)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b(he|she)\s+(don't)\b""", RegexOption.IGNORE_CASE),
        )
        val errorCount = errorPatterns.count { it.containsMatchIn(text) }
        score -= errorCount.coerceAtMost(3) * 0.5f

        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        if (sentences.size >= 2) {
            val lengths = sentences.map { it.split("\\s+".toRegex()).size }
            val stdDev = standardDeviation(lengths)
            score += when {
                stdDev >= 4.0 -> 0.5f
                stdDev >= 2.0 -> 0.25f
                else -> 0f
            }
        }

        return score.coerceIn(1.0f, 9.0f)
    }

    // ── Feedback builders ──────────────────────────────────────────

    private fun buildStrengths(score: BandScore, words: List<String>, text: String): List<String> {
        val strengths = mutableListOf<String>()
        val uniqueWords = words.map { it.lowercase() }.toSet()
        val ttr = if (words.isNotEmpty()) uniqueWords.size.toFloat() / words.size else 0f

        if (score.fluencyCoherence >= 6.0f) strengths.add("Good fluency with natural speech flow")
        if (score.lexicalResource >= 6.0f) strengths.add("Strong vocabulary range and word choice")
        if (ttr >= 0.7f) strengths.add("Good vocabulary diversity (TTR: ${"%.0f".format(ttr * 100)}%)")
        if (score.grammaticalRangeAccuracy >= 6.0f) strengths.add("Solid grammatical control")
        val connectiveHits = CONNECTIVES.count { text.lowercase().contains(it) }
        if (connectiveHits >= 2) strengths.add("Effective use of discourse connectors")
        val advancedUsed = ADVANCED_VOCAB.filter { text.lowercase().contains(it) }
        if (advancedUsed.isNotEmpty()) strengths.add("Uses advanced vocabulary: ${advancedUsed.take(3).joinToString()}")

        return strengths
    }

    private fun buildImprovements(score: BandScore, words: List<String>, text: String, part: IELTSPart): List<String> {
        val improvements = mutableListOf<String>()

        if (score.fluencyCoherence < 5.5f) improvements.add("Try to speak more fluently with fewer pauses")
        if (score.lexicalResource < 5.5f) improvements.add("Expand vocabulary to express ideas more precisely")

        val wordCount = words.size
        val expectedRange = when (part) {
            IELTSPart.PART_1 -> 15..50
            IELTSPart.PART_2 -> 100..300
            IELTSPart.PART_3 -> 30..80
        }
        if (wordCount < expectedRange.first) {
            improvements.add("Speak more — only $wordCount words (aim for ${expectedRange.first}+)")
        } else if (wordCount > expectedRange.last) {
            improvements.add("Be more concise — ${wordCount} words may be too long for ${part.name.replace("_", " ")}")
        }

        if (score.grammaticalRangeAccuracy < 5.5f) improvements.add("Work on grammatical accuracy and sentence variety")

        val connectiveHits = CONNECTIVES.count { text.lowercase().contains(it) }
        if (connectiveHits == 0) improvements.add("Use connecting words (however, furthermore, for example)")

        if (score.pronunciation <= 0f) improvements.add("Pronunciation assessment requires audio analysis (not available)")

        return improvements
    }

    private fun buildOverallFeedback(score: BandScore, part: IELTSPart, wordCount: Int): String {
        val overall = score.overall
        val partLabel = part.name.replace("_", " ")
        return when {
            overall >= 7.0f -> "Strong performance in $partLabel. Overall band estimate: ${"%.1f".format(overall)}. " +
                "Demonstrates good command of English with minor areas to polish."
            overall >= 5.5f -> "Competent performance in $partLabel. Overall band estimate: ${"%.1f".format(overall)}. " +
                "Solid foundation with clear areas for improvement. Focus on the suggestions below."
            overall >= 4.0f -> "Developing performance in $partLabel. Overall band estimate: ${"%.1f".format(overall)}. " +
                "Keep practicing — regular speaking practice will help build confidence and fluency."
            else -> "Needs improvement in $partLabel. Overall band estimate: ${"%.1f".format(overall)}. " +
                "Focus on building vocabulary, using complete sentences, and speaking at length."
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> {
        return text.split("\\s+".toRegex())
            .map { it.trim().removeSuffix(",").removeSuffix(".").removeSuffix("!").removeSuffix("?") }
            .filter { it.isNotBlank() }
    }

    private fun standardDeviation(values: List<Int>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return kotlin.math.sqrt(variance).toFloat()
    }

    companion object {
        private val CONNECTIVES = listOf(
            "however", "moreover", "furthermore", "in addition", "on the other hand",
            "for example", "for instance", "as a result", "consequently", "therefore",
            "in conclusion", "to sum up", "in my opinion", "personally", "actually",
            "to be honest", "i mean", "you know", "well",
        )

        private val FILLERS = listOf("um", "uh", "er", "ah")

        private val ADVANCED_VOCAB = listOf(
            "significant", "considerable", "substantial", "predominant", "inevitable",
            "comprehensive", "controversial", "perspective", "consequence", "phenomenon",
            "innovative", "sustainable", "authentic", "diverse", "elaborate",
            "facilitate", "implement", "demonstrate", "emphasize", "illustrate",
            "beneficial", "detrimental", "prevalent", "crucial", "fundamental",
            "adequate", "remarkable", "sophisticated", "mitigate", "enhance",
        )
    }
}
