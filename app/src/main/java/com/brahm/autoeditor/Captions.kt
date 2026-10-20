package com.brahm.autoeditor

/** Word timing from transcription. */
data class Word(val s: Double, val e: Double, val w: String)

/** Animated word-by-word ASS caption builder — mirror of captions.py. */
object Captions {
    private const val W = 1080
    private const val H = 1920

    private fun group(words: List<Word>, maxWords: Int = 4, maxSpan: Double = 2.2): List<List<Word>> {
        val groups = ArrayList<List<Word>>()
        var cur = ArrayList<Word>()
        for (w in words) {
            if (cur.isEmpty()) { cur.add(w); continue }
            if (cur.size >= maxWords || (w.e - cur[0].s) > maxSpan) {
                groups.add(cur); cur = ArrayList(); cur.add(w)
            } else cur.add(w)
        }
        if (cur.isNotEmpty()) groups.add(cur)
        return groups
    }

    private fun esc(t: String) = t.replace("\\", "\\\\").replace("{", "(").replace("}", ")")

    private fun tc(t0: Double): String {
        val t = if (t0 < 0) 0.0 else t0
        val h = (t / 3600).toInt(); val m = ((t % 3600) / 60).toInt(); val s = t % 60
        return String.format("%d:%02d:%05.2f", h, m, s)
    }

    fun buildAss(words: List<Word>, theme: Theme, lang: String, offset: Double = 0.0,
                 keywords: List<Keywords.Hit> = emptyList(),
                 fontChoice: String = "", textBg: Boolean = false,
                 textBgColor: String = "#000000", highlight: String = ""): String {
        val c = theme.caption
        val font = if (fontChoice.isNotBlank() && lang == "en") fontChoice else (Themes.LANG_FONT[lang] ?: c.fontLatin)
        val prim = Themes.assColor(c.primary)
        val hi = if (highlight.isNotBlank()) Themes.assColor(highlight) else Themes.assColor(c.highlight)
        val out = if (textBg) Themes.assColor(textBgColor) else Themes.assColor(c.outline)
        val border = if (textBg) 3 else 1
        val sb = StringBuilder()
        sb.append(
            """[Script Info]
ScriptType: v4.00+
PlayResX: $W
PlayResY: $H
WrapStyle: 2
ScaledBorderAndShadow: yes
YCbCr Matrix: TV.709

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Cap,$font,${c.size},$prim,$prim,$out,&H64000000&,-1,0,0,0,100,100,1,0,$border,${c.outlineW},${c.shadow},2,90,90,${c.marginV},1
Style: Pop,$font,128,$hi,$hi,${Themes.assColor(c.outline)},&H64000000&,-1,0,0,0,100,100,2,0,1,8,2,8,80,80,360,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
        )
        for (g in group(words)) {
            val ce = g.last().e
            val bounds = g.map { it.s } + ce
            for (i in g.indices) {
                val a = bounds[i] - offset
                var b = bounds[i + 1] - offset
                if (b <= a) b = a + 0.05
                val parts = g.mapIndexed { j, ww ->
                    val tok = esc(ww.w)
                    if (j == i) "{\\1c$hi\\fscx${c.pop}\\fscy${c.pop}}$tok{\\1c$prim\\fscx100\\fscy100}"
                    else tok
                }.joinToString(" ")
                val eff = if (i == 0) "{\\fad(${c.fadeIn},${c.fadeOut})}" else ""
                sb.append("Dialogue: 0,${tc(a)},${tc(b)},Cap,,0,0,0,,$eff$parts\n")
            }
        }
        // keyword pop-text (big animated word at top), layer above captions
        for (k in keywords) {
            val a = k.start - offset
            val b = minOf(k.start + 1.4, k.end) - offset
            if (b <= a) continue
            val tok = esc(k.keyword)
            val pop = "{\\fad(120,120)\\fscx55\\fscy55\\t(0,170,\\fscx116\\fscy116)" +
                "\\t(170,300,\\fscx100\\fscy100)}"
            sb.append("Dialogue: 1,${tc(a)},${tc(b)},Pop,,0,0,0,,$pop$tok\n")
        }
        return sb.toString()
    }
}
