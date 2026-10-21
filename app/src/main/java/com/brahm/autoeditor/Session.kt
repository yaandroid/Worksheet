package com.brahm.autoeditor

/** Shared state passed from the main screen to the edit/preview screen. */
object Session {
    var inputPath: String? = null
    var clips: MutableList<Clip> = mutableListOf()   // multi-clip timeline (videos + images)
    var words: MutableList<Word> = mutableListOf()
    var opt: AutoEditEngine.Options = AutoEditEngine.Options()
    var wantBroll: Boolean = false
    var bgMode: String = "none"   // none | green | ai
    var wantAutoTheme: Boolean = false
    var pexelsKey: String = ""
    var groqKey: String = ""
    var bgColor: String = "#0a1a33"
    var coverSec: Double = -1.0   // "Set cover" — poster frame time (<0 = default)

    /** Total timeline length from all clips. */
    fun totalDur(): Double = clips.sumOf { it.outDur() }.let { if (it > 0.1) it else 10.0 }
}
