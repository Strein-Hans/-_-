package com.ielts.coach.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.ielts.coach.R
import com.ielts.coach.data.local.AppDatabase
import com.ielts.coach.data.local.entity.SessionEntity
import com.ielts.coach.data.model.BandScore
import com.ielts.coach.data.model.ScoringReport
import com.ielts.coach.databinding.ActivityHistoryBinding
import com.ielts.coach.databinding.ItemHistoryBinding
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.ui.report.ReportActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadHistory()
    }

    private fun loadHistory() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val sessions = db.sessionDao().getAll()
            if (sessions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.rvHistory.layoutManager = LinearLayoutManager(this@HistoryActivity)
                binding.rvHistory.adapter = HistoryAdapter(sessions) { session ->
                    openReport(session)
                }
            }
        }
    }

    private fun openReport(entity: SessionEntity) {
        val report = ScoringReport(
            sessionId = entity.sessionId,
            part = entity.part,
            score = entity.score,
            userTranscript = entity.userTranscript,
            examinerTranscript = entity.examinerTranscript,
            wordCount = entity.wordCount,
            durationSeconds = entity.durationSeconds,
            strengths = entity.strengths,
            improvements = entity.improvements,
            overallFeedback = entity.overallFeedback,
            timestamp = entity.timestamp,
        )
        val intent = Intent(this, ReportActivity::class.java)
        intent.putExtra(ReportActivity.EXTRA_REPORT, Gson().toJson(report))
        startActivity(intent)
    }

    class HistoryAdapter(
        private val items: List<SessionEntity>,
        private val onClick: (SessionEntity) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val partLabel = item.part.name.replace("_", " ")
            holder.binding.tvPart.text = partLabel
            holder.binding.tvBand.text = item.score.overallBandString()

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.binding.tvDate.text = sdf.format(Date(item.timestamp))
            holder.binding.tvWords.text = "${item.wordCount} words"
            val min = item.durationSeconds / 60
            val sec = item.durationSeconds % 60
            holder.binding.tvDuration.text = String.format("%d:%02d", min, sec)

            holder.binding.root.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
