package com.brahm.autoeditor

/** One-tap style presets. "Custom" keeps the manual controls; others override look. */
object Templates {
    data class T(val name: String, val theme: String, val font: String,
                 val highlight: String, val music: Boolean, val pip: Boolean, val textBg: Boolean)

    val LIST = listOf(
        T("Custom (manual)", "", "", "", false, false, false),
        T("Clinical reel", "clinical_clean", "", "#FFC400", true, false, false),
        T("Bold social", "bold_social", "Anton", "#27E36B", true, true, true),
        T("Calm educational", "calm_edu", "", "#3EA6FF", false, false, false),
        T("Dark cinematic", "dark_cinema", "Bebas Neue", "#FFB020", true, false, true),
        T("Warm story", "warm_story", "", "#FF7A1A", true, false, false)
    )

    fun apply(base: AutoEditEngine.Options, index: Int): AutoEditEngine.Options {
        if (index <= 0 || index >= LIST.size) return base
        val t = LIST[index]
        return base.copy(theme = t.theme, fontChoice = t.font, highlight = t.highlight,
            music = t.music, pip = t.pip, textBg = t.textBg)
    }
}
