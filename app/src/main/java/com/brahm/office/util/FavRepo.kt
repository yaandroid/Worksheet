package com.brahm.office.util

import android.content.Context

/** Favorites — a persistent set of file paths the user has starred. */
object FavRepo {
    private const val PREF = "brahm_favorites"
    private const val KEY = "paths"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(c: Context): Set<String> = prefs(c).getStringSet(KEY, emptySet()) ?: emptySet()
    fun isFav(c: Context, path: String): Boolean = all(c).contains(path)

    fun toggle(c: Context, path: String): Boolean {
        val s = HashSet(all(c))
        val nowFav = if (s.contains(path)) { s.remove(path); false } else { s.add(path); true }
        prefs(c).edit().putStringSet(KEY, s).apply()
        return nowFav
    }

    fun setFav(c: Context, paths: Collection<String>, fav: Boolean) {
        val s = HashSet(all(c))
        if (fav) s.addAll(paths) else s.removeAll(paths.toSet())
        prefs(c).edit().putStringSet(KEY, s).apply()
    }
}
