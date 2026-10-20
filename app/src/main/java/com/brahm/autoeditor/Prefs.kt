package com.brahm.autoeditor

import android.content.Context

/** Stores the Groq AI key and last-used options on-device. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("brahm_autoeditor", Context.MODE_PRIVATE)
    var groqKey: String
        get() = sp.getString("groq_key", "") ?: ""
        set(v) { sp.edit().putString("groq_key", v).apply() }
    var theme: String
        get() = sp.getString("theme", "clinical_clean") ?: "clinical_clean"
        set(v) { sp.edit().putString("theme", v).apply() }
    var lang: String
        get() = sp.getString("lang", "en") ?: "en"
        set(v) { sp.edit().putString("lang", v).apply() }
    var brand: String
        get() = sp.getString("brand", "BRAHM") ?: "BRAHM"
        set(v) { sp.edit().putString("brand", v).apply() }
}
