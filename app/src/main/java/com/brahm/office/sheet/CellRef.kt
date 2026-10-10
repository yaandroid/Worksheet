package com.brahm.office.sheet

/** A1-style cell addressing. Columns A..ZZ (1..702), rows 1..MAX. */
object CellRef {
    const val MAX_ROWS = 10000
    const val MAX_COLS = 702          // A .. ZZ

    /** 0-based column index -> letters (0 -> A, 25 -> Z, 26 -> AA, 701 -> ZZ). */
    fun colName(col0: Int): String {
        var c = col0
        val sb = StringBuilder()
        while (c >= 0) {
            sb.append(('A' + (c % 26)))
            c = c / 26 - 1
        }
        return sb.reverse().toString()
    }

    /** letters -> 0-based column index. */
    fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters.uppercase()) {
            if (ch !in 'A'..'Z') break
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }

    /** "A1" -> Pair(row0, col0) or null. */
    fun parse(ref: String): Pair<Int, Int>? {
        val m = Regex("^\\$?([A-Za-z]{1,2})\\$?(\\d{1,6})$").find(ref.trim()) ?: return null
        val col = colIndex(m.groupValues[1])
        val row = m.groupValues[2].toInt() - 1
        if (col !in 0 until MAX_COLS || row !in 0 until MAX_ROWS) return null
        return row to col
    }

    fun name(row0: Int, col0: Int): String = "${colName(col0)}${row0 + 1}"

    /** packed key for sparse storage */
    fun key(row0: Int, col0: Int): Long = row0.toLong() * MAX_COLS + col0
}
