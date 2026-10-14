package com.brahm.office

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** BRAHM Office — Brahm InnerBrain Technologies Pvt. Ltd. */
class OfficeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One consistent light UI regardless of the phone's system dark mode.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
