package com.ielts.coach.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import com.ielts.coach.R
import com.ielts.coach.data.model.Accent
import com.ielts.coach.databinding.ActivitySettingsBinding
import com.ielts.coach.ui.common.BaseActivity
import com.ielts.coach.util.LocaleHelper
import android.widget.Toast

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var selectedMode = MODE_TEMPLATE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedSettings()
        setupListeners()
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Accent
        val accent = prefs.getString(KEY_ACCENT, Accent.BRITISH.code)
        if (accent == Accent.AMERICAN.code) {
            binding.chipGroupAccent.check(binding.chipAmerican.id)
        } else {
            binding.chipGroupAccent.check(binding.chipBritish.id)
        }

        // Language
        val lang = LocaleHelper.getSavedLanguage(this)
        if (lang == "zh") {
            binding.chipGroupLanguage.check(binding.chipChinese.id)
        } else {
            binding.chipGroupLanguage.check(binding.chipEnglish.id)
        }

        // Conversation mode
        selectedMode = prefs.getString(KEY_CONVERSATION_MODE, MODE_TEMPLATE) ?: MODE_TEMPLATE
        updateModeCards()

        // ASR config
        binding.etIflytekAppId.setText(prefs.getString(KEY_IFLYTEK_APP_ID, "05dd72b7"))
        binding.etIflytekApiKey.setText(prefs.getString(KEY_IFLYTEK_API_KEY, "dba1e0a679fb39dfc2a89f4eefe12420"))
        binding.etIflytekApiSecret.setText(prefs.getString(KEY_IFLYTEK_API_SECRET, "MzBkZjViMTdmMGU1MjNlZDA3NjQ0ZTgz"))

        // LLM config
        binding.etLlmEndpoint.setText(
            prefs.getString(KEY_LLM_ENDPOINT, "https://api.deepseek.com/v1/chat/completions")
        )
        binding.etLlmApiKey.setText(prefs.getString(KEY_LLM_API_KEY, "sk-71b035d3a17e44a9b0a100182ade17a7"))

        // Backend URL
        binding.etBackendUrl.setText(
            prefs.getString(KEY_BACKEND_URL, "http://8.136.188.53:8001")
        )
    }

    private fun updateModeCards() {
        val selectedStroke = resources.getColor(R.color.mode_selected_stroke, null)
        val selectedBg = resources.getColor(R.color.mode_selected_bg, null)
        val unselectedStroke = resources.getColor(R.color.mode_unselected_stroke, null)
        val unselectedBg = resources.getColor(R.color.mode_unselected_bg, null)

        val cards = listOf(binding.cardModeTemplate, binding.cardModeLlm, binding.cardModeBackend)
        val checks = listOf(binding.ivCheckTemplate, binding.ivCheckLlm, binding.ivCheckBackend)
        val modes = listOf(MODE_TEMPLATE, MODE_LLM, MODE_BACKEND)

        for (i in modes.indices) {
            val isSelected = modes[i] == selectedMode
            cards[i].strokeColor = if (isSelected) selectedStroke else unselectedStroke
            cards[i].setCardBackgroundColor(if (isSelected) selectedBg else unselectedBg)
            checks[i].setImageResource(
                if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked
            )
        }
    }

    private fun setupListeners() {
        binding.cardModeTemplate.setOnClickListener {
            selectedMode = MODE_TEMPLATE
            updateModeCards()
        }

        binding.cardModeLlm.setOnClickListener {
            selectedMode = MODE_LLM
            updateModeCards()
        }

        binding.cardModeBackend.setOnClickListener {
            selectedMode = MODE_BACKEND
            updateModeCards()
        }

        // Advanced settings toggle
        binding.tvAdvancedToggle.setOnClickListener {
            val layout = binding.layoutAdvanced
            if (layout.visibility == View.VISIBLE) {
                layout.visibility = View.GONE
                binding.tvAdvancedToggle.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, R.drawable.ic_expand_more, 0
                )
            } else {
                layout.visibility = View.VISIBLE
                binding.tvAdvancedToggle.setCompoundDrawablesWithIntrinsicBounds(
                    0, 0, R.drawable.ic_expand_less, 0
                )
            }
        }

        binding.btnSave.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()

            // Accent
            val accent = if (binding.chipAmerican.isChecked) Accent.AMERICAN.code else Accent.BRITISH.code
            prefs.putString(KEY_ACCENT, accent)

            // Language
            val lang = if (binding.chipChinese.isChecked) "zh" else "en"
            LocaleHelper.saveLanguage(this, lang)

            // Conversation mode
            prefs.putString(KEY_CONVERSATION_MODE, selectedMode)

            // ASR
            prefs.putString(KEY_IFLYTEK_APP_ID, binding.etIflytekAppId.text.toString())
            prefs.putString(KEY_IFLYTEK_API_KEY, binding.etIflytekApiKey.text.toString())
            prefs.putString(KEY_IFLYTEK_API_SECRET, binding.etIflytekApiSecret.text.toString())

            // LLM
            prefs.putString(KEY_LLM_ENDPOINT, binding.etLlmEndpoint.text.toString())
            prefs.putString(KEY_LLM_API_KEY, binding.etLlmApiKey.text.toString())

            // Backend URL
            prefs.putString(KEY_BACKEND_URL, binding.etBackendUrl.text.toString())

            prefs.apply()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()

            if (lang != LocaleHelper.getSavedLanguage(this)) {
                LocaleHelper.setLocale(this, lang)
                recreate()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "ielts_coach_prefs"
        private const val KEY_ACCENT = "accent"
        private const val KEY_CONVERSATION_MODE = "conversation_mode"
        private const val KEY_IFLYTEK_APP_ID = "iflytek_app_id"
        private const val KEY_IFLYTEK_API_KEY = "iflytek_api_key"
        private const val KEY_IFLYTEK_API_SECRET = "iflytek_api_secret"
        private const val KEY_LLM_ENDPOINT = "llm_endpoint"
        private const val KEY_LLM_API_KEY = "llm_api_key"
        private const val KEY_BACKEND_URL = "backend_url"

        private const val MODE_TEMPLATE = "template"
        private const val MODE_LLM = "llm"
        private const val MODE_BACKEND = "backend"
    }
}
