package com.brahm.autoeditor

import android.app.AlertDialog
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.brahm.autoeditor.databinding.ActivityTimelineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Timeline screen (Stage 1): all elements as blocks on tracks; tap to edit; Export compiles. */
class TimelineActivity : AppCompatActivity() {

    private lateinit var b: ActivityTimelineBinding
    private var outputPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTimelineBinding.inflate(layoutInflater)
        setContentView(b.root)

        val dur = durationSec(Session.inputPath ?: "")
        refreshTimeline(dur)

        // live preview: raw video + overlay, scrub by dragging timeline
        Session.inputPath?.let { b.vv.setVideoPath(it); b.vv.seekTo(1) }
        b.ov.theme = Themes.byId(Session.opt.theme)
        b.ov.brand = Session.opt.brand; b.ov.tag = Session.opt.tag
        b.ov.popText = Session.opt.popText; b.ov.captionMargin = Session.opt.captionMargin
        b.tl.onSeek = { t -> if (!exported) { b.vv.seekTo((t * 1000).toInt()); b.ov.setTime(t) } }

        b.tl.onSelect = { track, index -> if (track == 1) editCaption(index) else editBroll(index) }
        b.tl.onEdited = { b.ov.invalidate() }
        b.btnZoomIn.setOnClickListener { b.tl.zoomIn() }
        b.btnZoomOut.setOnClickListener { b.tl.zoomOut() }
        b.btnPlay.setOnClickListener { togglePlay() }
        b.btnExport.setOnClickListener { export() }
        b.btnShare.setOnClickListener { share() }
        b.btnTText.setOnClickListener { textDialog() }
        b.btnTAudio.setOnClickListener { audioDialog() }
        b.btnTBroll.setOnClickListener { brollDialog() }
        b.btnTTheme.setOnClickListener { themeDialog() }
        b.btnTBg.setOnClickListener { bgDialog() }
        b.btnTFx.setOnClickListener { fxDialog() }
        b.btnTTemplate.setOnClickListener { templateDialog() }
    }

    // ---- CapCut-style tool panels (edit Session.opt live) ----
    private fun syncPreview() {
        b.ov.theme = Themes.byId(Session.opt.theme)
        b.ov.brand = Session.opt.brand; b.ov.tag = Session.opt.tag
        b.ov.popText = Session.opt.popText; b.ov.captionMargin = Session.opt.captionMargin
        b.ov.invalidate(); b.tl.musicOn = Session.opt.music; b.tl.invalidate()
    }
    private fun lbl(t: String) = android.widget.TextView(this).apply { text = t; setTextColor(0xFF9AA6CC.toInt()); setPadding(0, 16, 0, 4) }
    private fun spin(items: List<String>, sel: Int) = android.widget.Spinner(this).apply {
        adapter = android.widget.ArrayAdapter(this@TimelineActivity, android.R.layout.simple_spinner_dropdown_item, items); setSelection(sel.coerceIn(0, items.size - 1))
    }
    private fun swi(t: String, c: Boolean) = com.google.android.material.switchmaterial.SwitchMaterial(this).apply { text = t; isChecked = c; setTextColor(0xFFEEF2FF.toInt()) }
    private fun edit(v: String) = android.widget.EditText(this).apply { setText(v); setTextColor(0xFFEEF2FF.toInt()) }
    private fun panel(vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 0); views.forEach { addView(it) }
    }
    private fun show(title: String, content: LinearLayout, onOk: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setView(androidx.core.widget.NestedScrollView(this).apply { addView(content) })
            .setPositiveButton("Apply") { _, _ -> onOk(); syncPreview() }.setNegativeButton("Cancel", null).show()
    }

    private val fonts = listOf("Default (Poppins)", "Anton", "Bebas Neue")
    private val positions = listOf("Bottom", "Lower", "Middle", "Upper")
    private val denoises = listOf("off", "light", "medium", "strong")

    private fun textDialog() {
        val o = Session.opt
        val spFont = spin(fonts, fonts.indexOf(if (o.fontChoice.isBlank()) "Default (Poppins)" else o.fontChoice).coerceAtLeast(0))
        val posIdx = when (o.captionMargin) { 160 -> 0; 850 -> 2; 1300 -> 3; else -> 1 }
        val spPos = spin(positions, posIdx)
        val swBox = swi("Text background box", o.textBg)
        val etHi = edit(o.highlight)
        val swPop = swi("Keyword pop-text (bada shabd)", o.popText)
        show("Text", panel(lbl("Font"), spFont, lbl("Position"), spPos, swBox,
            lbl("Highlight color (khaali=theme)"), etHi, swPop)) {
            Session.opt = o.copy(
                fontChoice = if (spFont.selectedItemPosition == 0) "" else fonts[spFont.selectedItemPosition],
                captionMargin = when (spPos.selectedItemPosition) { 0 -> 160; 2 -> 850; 3 -> 1300; else -> 430 },
                textBg = swBox.isChecked, highlight = etHi.text.toString().trim(), popText = swPop.isChecked)
        }
    }
    private fun audioDialog() {
        val o = Session.opt
        val swMusic = swi("Background music", o.music)
        val swVoice = swi("Voice clear", o.voiceClear)
        val spDen = spin(denoises, denoises.indexOf(o.denoise).coerceAtLeast(0))
        val sbPitch = android.widget.SeekBar(this).apply { max = 12; progress = (o.pitch + 6).toInt() }
        show("Audio", panel(swMusic, swVoice, lbl("Noise removal"), spDen, lbl("Pitch (-6..+6)"), sbPitch)) {
            Session.opt = o.copy(music = swMusic.isChecked, voiceClear = swVoice.isChecked,
                denoise = denoises[spDen.selectedItemPosition], pitch = (sbPitch.progress - 6).toDouble())
        }
    }
    private fun brollDialog() {
        val swAuto = swi("Auto b-roll images", Session.wantBroll)
        val swPip = swi("PiP (b-roll fullscreen + circle)", Session.opt.pip)
        val etPex = edit(Session.pexelsKey)
        show("B-roll", panel(swAuto, swPip, lbl("Pexels API key (optional)"), etPex)) {
            Session.wantBroll = swAuto.isChecked; Session.pexelsKey = etPex.text.toString()
            Session.opt = Session.opt.copy(pip = swPip.isChecked)
            refreshTimeline(b.tl.totalSec)
        }
    }
    private fun themeDialog() {
        val ids = Themes.ALL.map { it.label }
        val cur = Themes.ALL.indexOfFirst { it.id == Session.opt.theme }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(ids.toTypedArray(), cur) { d, w ->
            Session.opt = Session.opt.copy(theme = Themes.ALL[w].id); syncPreview(); d.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun bgDialog() {
        val modes = listOf("None", "Green screen (fast)", "AI remove (slow)")
        val cur = when (Session.bgMode) { "green" -> 1; "ai" -> 2; else -> 0 }
        val etCol = edit(Session.bgColor)
        val sp = spin(modes, cur)
        show("Background", panel(lbl("Mode"), sp, lbl("Naya background color"), etCol)) {
            Session.bgMode = when (sp.selectedItemPosition) { 1 -> "green"; 2 -> "ai"; else -> "none" }
            Session.bgColor = etCol.text.toString().ifBlank { "#0a1a33" }
            Session.opt = Session.opt.copy(bgMode = if (Session.bgMode == "green") "green" else "none", bgColor = Session.bgColor)
        }
    }
    private fun fxDialog() {
        val swTheme = swi("AI auto-theme", Session.wantAutoTheme)
        val swPop = swi("Keyword pop-text", Session.opt.popText)
        show("Effects", panel(swTheme, swPop)) {
            Session.wantAutoTheme = swTheme.isChecked
            Session.opt = Session.opt.copy(popText = swPop.isChecked)
        }
    }
    private fun templateDialog() {
        val names = Templates.LIST.map { it.name }
        AlertDialog.Builder(this).setTitle("Template").setItems(names.toTypedArray()) { _, w ->
            Session.opt = Templates.apply(Session.opt, w); syncPreview()
        }.setNegativeButton("Cancel", null).show()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var playing = false
    private var exported = false
    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            val t = b.vv.currentPosition / 1000.0
            if (!exported) { b.ov.setTime(t); b.tl.seekTo(t) }
            if (b.vv.isPlaying) handler.postDelayed(this, 40) else { playing = false; b.btnPlay.text = "▶ Play" }
        }
    }
    private fun togglePlay() {
        if (playing) { b.vv.pause(); playing = false; b.btnPlay.text = "▶ Play" }
        else {
            if (!exported) b.vv.seekTo((b.tl.currentTime() * 1000).toInt())
            b.vv.start(); playing = true; b.btnPlay.text = "⏸ Pause"; handler.post(tick)
        }
    }

    private fun refreshTimeline(dur: Double) {
        val kws = Keywords.extract(Session.words)
        b.tl.captions = Session.words
        b.tl.broll = kws.map { Broll.Placed("", it.start, it.end) }.toMutableList()
        b.tl.musicOn = Session.opt.music
        b.tl.totalSec = if (dur > 0) dur else (Session.words.lastOrNull()?.e ?: 10.0)
        b.tl.invalidate()
        b.ov.words = Session.words; b.ov.keywords = kws; b.ov.invalidate()
    }

    private fun durationSec(path: String): Double = try {
        if (path.isEmpty()) 0.0 else {
            val r = MediaMetadataRetriever(); r.setDataSource(path)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            r.release(); ms / 1000.0
        }
    } catch (e: Exception) { 0.0 }

    private fun editCaption(i: Int) {
        val w = Session.words.getOrNull(i) ?: return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 0) }
        val txt = EditText(this).apply { setText(w.w); hint = "text" }
        val st = EditText(this).apply { setText(w.s.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "start sec" }
        val en = EditText(this).apply { setText(w.e.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "end sec" }
        box.addView(txt); box.addView(st); box.addView(en)
        AlertDialog.Builder(this).setTitle("Edit caption").setView(box)
            .setPositiveButton("OK") { _, _ ->
                val s = st.text.toString().toDoubleOrNull() ?: w.s
                val e = en.text.toString().toDoubleOrNull() ?: w.e
                Session.words[i] = Word(s, e, txt.text.toString())
                b.tl.invalidate()
            }
            .setNegativeButton("Delete") { _, _ -> Session.words.removeAt(i); refreshTimeline(b.tl.totalSec) }
            .setNeutralButton("Cancel", null).show()
    }

    private fun editBroll(i: Int) {
        // b-roll blocks are keyword-driven; tapping shows which keyword triggers it
        val p = b.tl.broll.getOrNull(i) ?: return
        AlertDialog.Builder(this).setTitle("B-roll")
            .setMessage("Keyword b-roll at %.1f–%.1f s.\nImage auto aati hai (library/Pexels/AI). Keyword text badalna ho to Captions me shabd badlo."
                .format(p.start, p.end))
            .setPositiveButton("OK", null).show()
    }

    private fun export() {
        val input = Session.inputPath ?: return
        val words = Session.words.toList()
        val keywords = Keywords.extract(words)
        setBusy(true, "Starting…", 5)
        lifecycleScope.launch {
            try {
                if (Session.wantAutoTheme) {
                    setBusy(true, "AI theme…", 15)
                    withContext(Dispatchers.IO) { GroqTheme.pick(words.joinToString(" ") { it.w }, Session.groqKey) }
                        ?.let { Session.opt = Session.opt.copy(theme = it) }
                }
                val broll = if (Session.wantBroll) withContext(Dispatchers.IO) {
                    runOnUiThread { setBusy(true, "b-roll images…", 25) }
                    Broll.resolve(this@TimelineActivity, keywords, Session.pexelsKey, useAi = true)
                } else emptyList()
                var renderInput = input
                if (Session.bgMode == "ai") renderInput = withContext(Dispatchers.IO) {
                    BgRemover.run(this@TimelineActivity, input, Session.bgColor) { s, p -> runOnUiThread { setBusy(true, s, p) } }
                }
                AutoEditEngine(this@TimelineActivity).render(renderInput, words, Session.opt, broll, keywords,
                    object : AutoEditEngine.Listener {
                        override fun onProgress(pct: Int, step: String) = runOnUiThread { setBusy(true, step, pct) }
                        override fun onDone(o: String) = runOnUiThread {
                            outputPath = o; setBusy(false, "Export ready ✓", 100)
                            exported = true; playing = false; b.ov.visibility = android.view.View.GONE
                            b.vv.setVideoPath(o); b.vv.start(); b.btnShare.isEnabled = true
                        }
                        override fun onError(m: String) = runOnUiThread { setBusy(false, "Error: $m", 0) }
                    })
            } catch (e: Exception) { runOnUiThread { setBusy(false, "Error: ${e.message}", 0) } }
        }
    }

    private fun setBusy(busy: Boolean, step: String, pct: Int) {
        b.btnExport.isEnabled = !busy; b.progress.progress = pct; b.tvStatus.text = "$step  ($pct%)"
    }

    private fun share() {
        val out = outputPath ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(out))
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = "video/mp4"; i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(android.content.Intent.createChooser(i, "Share reel"))
    }
}
