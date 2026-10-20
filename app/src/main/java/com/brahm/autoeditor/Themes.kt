package com.brahm.autoeditor

/** Theme presets — mirror of the web engine's themes.py. */
data class CaptionStyle(
    val fontLatin: String, val size: Int, val primary: String, val highlight: String,
    val outline: String, val outlineW: Int, val shadow: Int, val marginV: Int,
    val pop: Int, val fadeIn: Int, val fadeOut: Int
)

data class Theme(
    val id: String, val label: String, val grade: String,
    val caption: CaptionStyle, val zoomAmp: Double, val zoomSpeed: Int,
    val brandColor: String, val music: String
)

object Themes {
    val ALL: List<Theme> = listOf(
        Theme("clinical_clean", "Clinical Clean",
            "eq=contrast=1.08:saturation=1.12:brightness=0.012:gamma=0.98," +
                "curves=r='0/0.02 1/0.98':b='0/0.05 1/0.96'",
            CaptionStyle("Poppins", 76, "#FFFFFF", "#FFC400", "#101010", 7, 3, 430, 112, 90, 60),
            0.04, 60, "#FFFFFF", "calm"),
        Theme("bold_social", "Bold Social",
            "eq=contrast=1.14:saturation=1.25:brightness=0.015,unsharp=5:5:0.6",
            CaptionStyle("Poppins", 90, "#FFFFFF", "#27E36B", "#0A0A0A", 9, 2, 470, 122, 70, 50),
            0.07, 45, "#FFFFFF", "upbeat"),
        Theme("calm_edu", "Calm Educational",
            "eq=contrast=1.04:saturation=1.06:brightness=0.008",
            CaptionStyle("Poppins", 72, "#FFFFFF", "#3EA6FF", "#14233A", 6, 2, 420, 108, 110, 80),
            0.025, 80, "#FFFFFF", "soft"),
        Theme("dark_cinema", "Dark Cinematic",
            "eq=contrast=1.12:saturation=1.05,curves=b='0/0.08 1/0.95':r='0/0 1/1.0',vignette=PI/5",
            CaptionStyle("Poppins", 78, "#F2F2F2", "#FFB020", "#000000", 7, 4, 440, 110, 120, 90),
            0.045, 70, "#F2F2F2", "cinematic"),
        Theme("warm_story", "Warm Story",
            "eq=contrast=1.06:saturation=1.16:brightness=0.014:gamma=0.97," +
                "curves=r='0/0.03 1/1.0':g='0/0.02 1/0.99':b='0/0.0 1/0.94'",
            CaptionStyle("Poppins", 76, "#FFF6E6", "#FF7A1A", "#241306", 7, 3, 430, 112, 100, 70),
            0.05, 65, "#FFF6E6", "warm")
    )

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** language code -> libass font family bundled in assets/fonts */
    val LANG_FONT = mapOf(
        "en" to "Poppins", "hi" to "Noto Sans Devanagari", "mr" to "Noto Sans Devanagari",
        "ne" to "Noto Sans Devanagari", "gu" to "Noto Sans Gujarati",
        "bn" to "Noto Sans Bengali", "ta" to "Noto Sans Tamil", "te" to "Noto Sans Telugu",
        "kn" to "Noto Sans Kannada", "ml" to "Noto Sans Malayalam", "pa" to "Noto Sans Gurmukhi"
    )

    /** #RRGGBB -> ASS &H00BBGGRR& */
    fun assColor(hex: String): String {
        val h = hex.removePrefix("#")
        val r = h.substring(0, 2); val g = h.substring(2, 4); val b = h.substring(4, 6)
        return "&H00$b$g$r&".uppercase()
    }
}
