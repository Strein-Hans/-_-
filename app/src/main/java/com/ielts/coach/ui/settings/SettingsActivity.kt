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
        val mode = prefs.getString(KEY_CONVERSATION_MODE, MODE_TEMPLATE)
        when (mode) {
            MODE_BACKEND -> binding.chipGroupMode.check(binding.chipBackend.id)
            MODE_LLM -> binding.chipGroupMode.check(binding.chipLlm.id)
            else -> binding.chipGroupMode.check(binding.chipTemplate.id)
        }

        // ASR config
        binding.etIflytekAppId.setText(prefs.getString(KEY_IFLYTEK_APP_ID, ""))
        binding.etIflytekApiKey.setText(prefs.getString(KEY_IFLYTEK_API_KEY, ""))
        binding.etIflytekApiSecret.setText(prefs.getString(KEY_IFLYTEK_API_SECRET, ""))

        // LLM config
        binding.etLlmEndpoint.setText(
            prefs.getString(KEY_LLM_ENDPOINT, "https://api.openai.com/v1/chat/completions")
        )
        binding.etLlmApiKey.setText(prefs.getString(KEY_LLM_API_KEY, ""))

        // Backend URL
        binding.etBackendUrl.setText(
            prefs.getString(KEY_BACKEND_URL, "http://10.0.2.2:8000")
        )

        // Show/hide fields based on mode
        updateModeVisibility()
    }

    private fun setupListeners() {
        binding.chipGroupMode.setOnCheckedChangeListener { _, _ ->
            updateModeVisibility()
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
            val mode = when {
                binding.chipBackend.isChecked -> MODE_BACKEND
                binding.chipLlm.isChecked -> MODE_LLM
                else -> MODE_TEMPLATE
            }
            prefs.putString(KEY_CONVERSATION_MODE, mode)

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

    private fun updateModeVisibility() {
        val isLlm = binding.chipLlm.isChecked
        val isBackend = binding.chipBackend.isChecked

        binding.tvLlmLabel.visibility = if (isLlm) View.VISIBLE else View.GONE
        binding.tilLlmEndpoint.visibility = if (isLlm) View.VISIBLE else View.GONE
        binding.tilLlmApiKey.visibility = if (isLlm) View.VISIBLE else View.GONE

        binding.tilBackendUrl.visibility = if (isBackend) View.VISIBLE else View.GONE
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
