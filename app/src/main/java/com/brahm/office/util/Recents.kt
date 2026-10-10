package com.brahm.office.util

import android.content.Context

/** Remembers recently opened documents (paths). */
object Recents {
    private const val PREF = "brahm_recents"
    private const val KEY = "paths"

    fun add(context: Context, path: String) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val cur = get(context).toMutableList()
        cur.remove(path)
        cur.add(0, path)
        while (cur.size > 40) cur.removeAt(cur.size - 1)
        sp.edit().putString(KEY, cur.joinToString("\n")).apply()
    }

    fun get(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }
}
