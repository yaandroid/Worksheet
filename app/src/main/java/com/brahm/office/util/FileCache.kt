package com.brahm.office.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the scanned file list so the home shows instantly and doesn't re-scan storage every time. */
object FileCache {
    private const val PREF = "brahm_filecache"
    private const val KEY = "list"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun save(c: Context, list: List<FileItem>) {
        try {
            val arr = JSONArray()
            for (f in list.take(5000)) {
                arr.put(JSONObject().apply {
                    put("n", f.name); put("p", f.path); put("s", f.size)
                    put("m", f.modified); put("t", f.type.name); put("f", f.folder)
                })
            }
            prefs(c).edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) {}
    }

    fun load(c: Context): List<FileItem> {
        return try {
            val s = prefs(c).getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(s); val out = ArrayList<FileItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = try { DocType.valueOf(o.getString("t")) } catch (e: Exception) { DocType.OTHER }
                // drop entries whose file no longer exists so the cache stays honest
                val f = java.io.File(o.getString("p"))
                if (f.exists()) out.add(FileItem(o.getString("n"), o.getString("p"), o.getLong("s"), o.getLong("m"), t, o.optString("f", "")))
            }
            out
        } catch (e: Exception) { emptyList() }
    }
}
