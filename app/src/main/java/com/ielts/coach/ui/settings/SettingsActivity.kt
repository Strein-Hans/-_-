package com.ielts.coach.ui.settings

import android.content.Context
import android.os.Bundle
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

        binding.etAppId.setText(prefs.getString(KEY_APP_ID, ""))
        binding.etAppKey.setText(prefs.getString(KEY_APP_KEY, ""))
        binding.etConversationId.setText(prefs.getString(KEY_CONVERSATION_ID, ""))

        val accent = prefs.getString(KEY_ACCENT, Accent.BRITISH.code)
        if (accent == Accent.AMERICAN.code) {
            binding.chipGroupAccent.check(binding.chipAmerican.id)
        } else {
            binding.chipGroupAccent.check(binding.chipBritish.id)
        }

        val lang = LocaleHelper.getSavedLanguage(this)
        if (lang == "zh") {
            binding.chipGroupLanguage.check(binding.chipChinese.id)
        } else {
            binding.chipGroupLanguage.check(binding.chipEnglish.id)
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()

            prefs.putString(KEY_APP_ID, binding.etAppId.text.toString())
            prefs.putString(KEY_APP_KEY, binding.etAppKey.text.toString())
            prefs.putString(KEY_CONVERSATION_ID, binding.etConversationId.text.toString())

            val accent = if (binding.chipAmerican.isChecked) Accent.AMERICAN.code else Accent.BRITISH.code
            prefs.putString(KEY_ACCENT, accent)

            val lang = if (binding.chipChinese.isChecked) "zh" else "en"
            LocaleHelper.saveLanguage(this, lang)

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
        private const val KEY_APP_ID = "app_id"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_CONVERSATION_ID = "conversation_id"
        private const val KEY_ACCENT = "accent"
    }
}
