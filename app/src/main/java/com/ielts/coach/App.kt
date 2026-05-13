package com.ielts.coach

import android.app.Application
import com.ielts.coach.util.LocaleHelper

class IELTSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.applySavedLocale(this)
    }
}
