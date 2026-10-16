package com.brahm.office.ui

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.brahm.office.R
import com.brahm.office.util.AppLock

/** Full-screen unlock gate: PIN + optional fingerprint. Cannot be bypassed with Back. */
class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        val pin = findViewById<EditText>(R.id.pinInput)
        val unlockBtn = findViewById<Button>(R.id.unlockBtn)
        val bioBtn = findViewById<Button>(R.id.bioBtn)
        val msg = findViewById<TextView>(R.id.lockMsg)

        fun tryPin() {
            if (AppLock.check(this, pin.text.toString())) { AppLock.unlocked = true; finish() }
            else { msg.text = "Wrong PIN, try again"; pin.text?.clear() }
        }
        unlockBtn.setOnClickListener { tryPin() }
        pin.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { tryPin(); true } else false }

        val bioOk = AppLock.useBio(this) && BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        if (bioOk) {
            bioBtn.visibility = android.view.View.VISIBLE
            bioBtn.setOnClickListener { showBiometric() }
            showBiometric()
        } else {
            bioBtn.visibility = android.view.View.GONE
        }
    }

    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.unlocked = true; finish()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BRAHM Office locked")
            .setSubtitle("Unlock with fingerprint")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        try { prompt.authenticate(info) } catch (_: Exception) {}
    }

    @Deprecated("Back is intentionally disabled on the lock screen")
    override fun onBackPressed() { moveTaskToBack(true) }
}
