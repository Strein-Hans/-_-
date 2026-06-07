package com.ielts.coach.ui.report

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.gson.Gson
import com.ielts.coach.R
import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.IELTSPart
import com.ielts.coach.data.model.ScoringReport
import com.ielts.coach.databinding.ActivityReportBinding
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.home.HomeActivity

class ReportActivity : BaseActivity() {

    private lateinit var binding: ActivityReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reportJson = intent.getStringExtra(EXTRA_REPORT)
        val report = if (reportJson != null) Gson().fromJson(reportJson, ScoringReport::class.java) else null

        if (report == null) {
            finish()
            return
        }

        bindReport(report)
        setupButtons()
    }

    private fun bindReport(report: ScoringReport) {
        val score = report.score

        binding.tvReportPart.text = formatPartLabel(report.part)
        binding.tvOverallScore.text = score.overallBandString()

        binding.tvDuration.text = formatDuration(report.durationSeconds)
        binding.tvWordCount.text = report.wordCount.toString()
        binding.tvWpm.text = String.format("%.1f", report.wordsPerMinute)

        bindDimension(binding.tvScoreFC, binding.progressFC, score.fluencyCoherence)
        bindDimension(binding.tvScoreLR, binding.progressLR, score.lexicalResource)
        bindDimension(binding.tvScoreGRA, binding.progressGRA, score.grammaticalRangeAccuracy)

        if (score.pronunciation <= 0f) {
            binding.tvScorePR.text = getString(R.string.not_available)
            binding.progressPR.progress = 0
        } else {
            bindDimension(binding.tvScorePR, binding.progressPR, score.pronunciation)
        }

        binding.tvTranscript.text = if (report.userTranscript.isNotBlank()) report.userTranscript else "-"

        // Examiner transcript
        if (report.examinerTranscript.isNotBlank()) {
            binding.cardExaminer.visibility = View.VISIBLE
            binding.tvExaminerTranscript.text = report.examinerTranscript
        }

        // Overall feedback
        if (report.overallFeedback.isNotBlank()) {
            binding.cardFeedback.visibility = View.VISIBLE
            binding.tvOverallFeedback.text = report.overallFeedback
        }

        // Strengths
        if (report.strengths.isNotEmpty()) {
            binding.cardStrengths.visibility = View.VISIBLE
            binding.tvStrengths.text = report.strengths.joinToString("\n") { "• $it" }
        }

        // Improvements
        if (report.improvements.isNotEmpty()) {
            binding.cardImprovements.visibility = View.VISIBLE
            binding.tvImprovements.text = report.improvements.joinToString("\n") { "• $it" }
        }

        // Corrections
        if (report.corrections.isNotEmpty()) {
            binding.cardCorrections.visibility = View.VISIBLE
            binding.tvCorrections.text = report.corrections.joinToString("\n\n") { c ->
                "✗ ${c.original}\n✓ ${c.corrected}\n  ${c.explanation}"
            }
        }

        // Vocabulary suggestions
        if (report.vocabularySuggestions.isNotEmpty()) {
            binding.cardVocabulary.visibility = View.VISIBLE
            binding.tvVocabulary.text = report.vocabularySuggestions.joinToString("\n\n") { v ->
                "${v.original} → ${v.suggested}\n  e.g. ${v.example}"
            }
        }
    }

    private fun bindDimension(
        textView: android.widget.TextView,
        progressBar: android.widget.ProgressBar,
        value: Float,
    ) {
        textView.text = BandScore().toBandString(value)
        progressBar.progress = (value * 10).toInt().coerceIn(0, 90)
    }

    private fun formatPartLabel(part: IELTSPart): String {
        return when (part) {
            IELTSPart.PART_1 -> getString(R.string.part1_title)
            IELTSPart.PART_2 -> getString(R.string.part2_title)
            IELTSPart.PART_3 -> getString(R.string.part3_title)
        }
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    private fun setupButtons() {
        binding.btnPracticeAgain.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        binding.btnExit.setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_REPORT = "extra_report"
    }
}
