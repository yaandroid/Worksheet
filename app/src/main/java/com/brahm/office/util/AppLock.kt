package com.brahm.office.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.brahm.office.ui.LockActivity
import java.security.MessageDigest

/** Simple local app-lock: a SHA-256 PIN + optional fingerprint, re-locks when the app is backgrounded. */
object AppLock {
    private const val PREF = "brahm_lock"
    private const val K_HASH = "pin_hash"
    private const val K_BIO = "use_bio"

    @Volatile var unlocked = false

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    fun isEnabled(c: Context): Boolean = !prefs(c).getString(K_HASH, null).isNullOrEmpty()
    fun useBio(c: Context): Boolean = prefs(c).getBoolean(K_BIO, false)
    fun setBio(c: Context, on: Boolean) { prefs(c).edit().putBoolean(K_BIO, on).apply() }

    private fun hash(pin: String): String {
        val b = MessageDigest.getInstance("SHA-256").digest(("BRAHM-salt-9x7-" + pin).toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }
    fun setPin(c: Context, pin: String) { prefs(c).edit().putString(K_HASH, hash(pin)).apply(); unlocked = true }
    fun clear(c: Context) { prefs(c).edit().remove(K_HASH).remove(K_BIO).apply(); unlocked = true }
    fun check(c: Context, pin: String): Boolean = isEnabled(c) && prefs(c).getString(K_HASH, null) == hash(pin)

    fun lock() { unlocked = false }

    /** If a lock is set and this session isn't unlocked, show the lock screen. Returns true if it locked. */
    fun guard(a: Activity): Boolean {
        if (isEnabled(a) && !unlocked) {
            try { a.startActivity(Intent(a, LockActivity::class.java)) } catch (_: Exception) {}
            return true
        }
        return false
    }
}
