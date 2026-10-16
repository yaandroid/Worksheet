package com.brahm.office

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.brahm.office.util.AppLock

/** BRAHM Office — Brahm InnerBrain Technologies Pvt. Ltd. */
class OfficeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // One consistent light UI regardless of the phone's system dark mode.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        // Re-lock whenever the whole app goes to the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { AppLock.lock() }
        })
    }
}
