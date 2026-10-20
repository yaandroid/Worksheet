package com.brahm.autoeditor

/** Shared state passed from the main screen to the edit/preview screen. */
object Session {
    var inputPath: String? = null
    var words: MutableList<Word> = mutableListOf()
    var opt: AutoEditEngine.Options = AutoEditEngine.Options()
    var wantBroll: Boolean = false
    var wantBg: Boolean = false
    var wantAutoTheme: Boolean = false
    var pexelsKey: String = ""
    var groqKey: String = ""
    var bgColor: String = "#0a1a33"
}
