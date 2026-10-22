package com.brahm.autoeditor

import android.app.AlertDialog
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.brahm.autoeditor.databinding.ActivityTimelineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** CapCut-style multi-clip editor: videos + images on a timeline, per-clip
 *  trim / speed / crop / replace / delete / sort, captions, audio, export. */
@UnstableApi
class TimelineActivity : AppCompatActivity() {

    private lateinit var b: ActivityTimelineBinding
    private var outputPath: String? = null
    private var selectedClip = -1
    private var player: ExoPlayer? = null
    private var offsets = DoubleArray(0)   // composed start time (sec) per clip

    // ---- media pickers ----
    private val addVideos = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.forEach { importClip(it, isImage = false) }
        refreshTimeline(); Toast.makeText(this, "${uris?.size ?: 0} video add", Toast.LENGTH_SHORT).show()
    }
    private val addImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris?.forEach { importClip(it, isImage = true) }
        refreshTimeline(); Toast.makeText(this, "${uris?.size ?: 0} image add", Toast.LENGTH_SHORT).show()
    }
    private var replaceTarget = -1
    private val replacePick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && replaceTarget in Session.clips.indices) {
            val isImg = contentResolver.getType(uri)?.startsWith("image") == true
            val f = copyIn(uri, if (isImg) "jpg" else "mp4") ?: return@registerForActivityResult
            pushUndo()
            val c = Session.clips[replaceTarget]
            if (isImg) { c.path = f; c.isImage = true }
            else { val d = durationSec(f); c.path = f; c.isImage = false; c.srcIn = 0.0; c.srcOut = d; c.srcDur = d }
            refreshTimeline()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTimelineBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.ov.lang = Session.opt.lang
        initPlayer()
        refreshTimeline(); syncPreview()

        b.tl.onSeek = { t -> if (!exported) seekComposed(t) }
        b.tl.onSelect = { track, index ->
            when (track) { 0 -> { selectClip(index); clipSheet(index) }; 1 -> editCaption(index); 2 -> editBroll(index) }
        }
        b.tl.onEdited = { b.ov.invalidate() }
        b.tl.onEditBegin = { pushUndo() }
        b.tl.onClipTrimmed = { refreshTimeline() }

        // top bar
        b.btnCancel.setOnClickListener { finish() }
        b.btnSaveTop.setOnClickListener { export() }
        b.spRes.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("720p", "1080p"))
        b.spRes.setSelection(1)

        // transport
        b.btnPlay.setOnClickListener { togglePlay() }
        b.btnUndo.setOnClickListener { doUndo() }
        b.btnRedo.setOnClickListener { doRedo() }
        b.btnZoomIn.setOnClickListener { b.tl.zoomIn() }
        b.btnZoomOut.setOnClickListener { b.tl.zoomOut() }
        b.btnShare.setOnClickListener { share() }

        // contextual clip tools (act on selected clip, else first clip)
        b.btnSplit.setOnClickListener { curClip()?.let { splitClip(it) } }
        b.btnReplace.setOnClickListener { curClip()?.let { replaceTarget = it; replacePick.launch("*/*") } }
        b.btnDelete.setOnClickListener { curClip()?.let { deleteClip(it) } }
        b.btnCrop.setOnClickListener { curClip()?.let { toggleCrop(it) } }
        b.btnSpeed.setOnClickListener { curClip()?.let { speedDialog(it) } }
        b.btnSort.setOnClickListener { curClip()?.let { sortDialog(it) } }
        b.btnMute.setOnClickListener { curClip()?.let { toggleMute(it) } }
        b.btnOrigSound.setOnClickListener { curClip()?.let { toggleMute(it) } }
        b.btnSetCover.setOnClickListener { setCover() }
        b.btnAdd.setOnClickListener { addMediaChooser() }

        // bottom main toolbar
        b.btnTTrim.setOnClickListener { curClip()?.let { trimDialog(it) } }
        b.btnTAudio.setOnClickListener { audioDialog() }
        b.btnTText.setOnClickListener { textDialog() }
        b.btnTAdjust.setOnClickListener { adjustDialog() }
        b.btnTFilter.setOnClickListener { filterDialog() }
        b.btnTFrame.setOnClickListener { frameDialog() }
    }

    /** The clip the tools act on: the selected one, else the first. */
    private fun curClip(): Int? {
        if (Session.clips.isEmpty()) { Toast.makeText(this, "Pehle video/clip add karo", Toast.LENGTH_SHORT).show(); return null }
        val i = if (selectedClip in Session.clips.indices) selectedClip else 0
        if (selectedClip !in Session.clips.indices) selectClip(0)
        return i
    }
    private fun addMediaChooser() {
        AlertDialog.Builder(this).setTitle("Add media")
            .setItems(arrayOf("🎬 Video (ek ya zyada)", "🖼 Image (ek ya zyada)")) { _, w ->
                if (w == 0) addVideos.launch("video/*") else addImages.launch("image/*")
            }.show()
    }
    private fun deleteClip(i: Int) {
        if (Session.clips.size <= 1) { Toast.makeText(this, "Kam se kam 1 clip chahiye", Toast.LENGTH_SHORT).show(); return }
        pushUndo(); Session.clips.removeAt(i); selectedClip = -1; refreshTimeline()
    }
    private fun toggleCrop(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        pushUndo(); c.fit = !c.fit; refreshTimeline()
        Toast.makeText(this, if (c.fit) "Fit (pura dikhe)" else "Fill (crop)", Toast.LENGTH_SHORT).show()
    }
    private fun toggleMute(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        val mute = c.volume <= 0.001
        pushUndo(); c.volume = if (mute) 1.0 else 0.0; refreshTimeline()
        Toast.makeText(this, if (mute) "Original sound ON" else "Muted", Toast.LENGTH_SHORT).show()
    }
    private fun sortDialog(i: Int) {
        AlertDialog.Builder(this).setTitle("Clip ${i + 1} — move")
            .setItems(arrayOf("◀ Left", "▶ Right")) { _, w -> moveClip(i, if (w == 0) -1 else 1) }.show()
    }

    private fun copyIn(uri: Uri, ext: String): String? = try {
        val f = File(cacheDir, "media_${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
        contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.absolutePath
    } catch (e: Exception) { null }

    private fun importClip(uri: Uri, isImage: Boolean) {
        val f = copyIn(uri, if (isImage) "jpg" else "mp4") ?: return
        pushUndo()
        if (isImage) Session.clips.add(Clip(path = f, isImage = true, imageDur = 3.0, label = "img"))
        else { val d = durationSec(f); Session.clips.add(Clip(path = f, isImage = false, srcIn = 0.0, srcOut = d, srcDur = d, label = "clip")) }
    }

    // ---- composed multi-clip preview (ExoPlayer) ----
    private fun initPlayer() {
        val p = ExoPlayer.Builder(this).build()
        b.pv.player = p
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying; b.btnPlay.text = if (isPlaying) "⏸" else "▶"
                if (isPlaying) handler.post(tick)
            }
        })
        player = p
        buildPlaylist()
    }

    /** Rebuild the preview playlist from the current clips (videos clipped to
     *  trim, images shown for their duration). Called after any clip edit. */
    private fun buildPlaylist() {
        val p = player ?: return
        if (exported) return
        offsets = DoubleArray(Session.clips.size)
        var acc = 0.0
        val items = ArrayList<MediaItem>()
        for ((i, c) in Session.clips.withIndex()) {
            offsets[i] = acc; acc += c.outDur()
            val uri = android.net.Uri.fromFile(File(c.path))
            val mi = if (c.isImage) {
                MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.IMAGE_JPEG)
                    .setImageDurationMs((c.imageDur * 1000).toLong()).build()
            } else {
                MediaItem.Builder().setUri(uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((c.srcIn * 1000).toLong())
                        .setEndPositionMs((c.srcOut * 1000).toLong()).build()).build()
            }
            items.add(mi)
        }
        if (items.isEmpty()) return
        val keepIdx = p.currentMediaItemIndex.coerceIn(0, items.size - 1)
        p.setMediaItems(items); p.prepare(); p.playWhenReady = false
        try { p.seekTo(keepIdx, 0) } catch (e: Exception) { p.seekTo(0, 0) }
    }

    private fun composedTime(): Double {
        val p = player ?: return 0.0
        if (offsets.isEmpty()) return p.currentPosition / 1000.0
        val idx = p.currentMediaItemIndex.coerceIn(0, offsets.size - 1)
        return offsets[idx] + p.currentPosition / 1000.0
    }

    private fun seekComposed(t: Double) {
        val p = player ?: return
        if (exported || offsets.isEmpty()) { p.seekTo((t * 1000).toLong()); b.ov.setTime(t); return }
        var idx = 0
        for (i in Session.clips.indices) { if (t + 1e-3 >= offsets[i]) idx = i else break }
        val within = ((t - offsets[idx]) * 1000).toLong().coerceAtLeast(0)
        try { p.seekTo(idx, within) } catch (e: Exception) {}
        b.ov.setTime(t)
    }

    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
    override fun onPause() { super.onPause(); player?.pause() }

    // ---- undo / redo (clips + words + options) ----
    private data class Snap(val clips: MutableList<Clip>, val words: MutableList<Word>, val opt: AutoEditEngine.Options)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private fun snap() = Snap(
        Session.clips.map { it.copy() }.toMutableList(),
        Session.words.map { Word(it.s, it.e, it.w) }.toMutableList(), Session.opt)
    private fun pushUndo() { undoStack.addLast(snap()); if (undoStack.size > 50) undoStack.removeFirst(); redoStack.clear() }
    private fun restore(s: Snap) { Session.clips = s.clips; Session.words = s.words; Session.opt = s.opt; refreshTimeline(); syncPreview() }
    private fun doUndo() {
        if (undoStack.isEmpty()) { Toast.makeText(this, "Undo khatam", Toast.LENGTH_SHORT).show(); return }
        redoStack.addLast(snap()); restore(undoStack.removeLast())
    }
    private fun doRedo() { if (redoStack.isEmpty()) return; undoStack.addLast(snap()); restore(redoStack.removeLast()) }

    // ---- clip contextual tools (Split / Replace / Delete / Crop / Speed / Sort / Trim / Sound) ----
    private fun clipSheet(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        val mute = c.volume <= 0.001
        val items = arrayOf("✂ Split", "✎ Trim", "⏩ Change speed", "⛶ Crop (fill/fit)",
            "🔁 Replace", if (mute) "🔊 Original sound ON" else "🔇 Mute (sound off)",
            "◀ Move left", "▶ Move right", "🗑 Delete")
        AlertDialog.Builder(this).setTitle("Clip ${i + 1} ${if (c.isImage) "(image)" else ""}")
            .setItems(items) { _, w ->
                when (w) {
                    0 -> splitClip(i)
                    1 -> trimDialog(i)
                    2 -> speedDialog(i)
                    3 -> { pushUndo(); c.fit = !c.fit; refreshTimeline(); Toast.makeText(this, if (c.fit) "Fit (full dikhe)" else "Fill (crop)", Toast.LENGTH_SHORT).show() }
                    4 -> { replaceTarget = i; replacePick.launch("*/*") }
                    5 -> { pushUndo(); c.volume = if (mute) 1.0 else 0.0; refreshTimeline() }
                    6 -> moveClip(i, -1)
                    7 -> moveClip(i, 1)
                    8 -> { if (Session.clips.size <= 1) { Toast.makeText(this, "Kam se kam 1 clip chahiye", Toast.LENGTH_SHORT).show() } else { pushUndo(); Session.clips.removeAt(i); selectedClip = -1; refreshTimeline() } }
                }
            }.setNegativeButton("Band karo", null).show()
    }

    /** Select a clip: highlight on timeline + jump the preview to its start. */
    private fun selectClip(i: Int) {
        selectedClip = i; b.tl.selectedClip = i; b.tl.invalidate()
        if (exported || i !in Session.clips.indices) return
        val start = offsets.getOrElse(i) { 0.0 }
        try { player?.seekTo(i, 0) } catch (e: Exception) {}
        b.ov.setTime(start); b.tl.seekTo(start)
    }

    private fun moveClip(i: Int, dir: Int) {
        val j = i + dir
        if (j !in Session.clips.indices) return
        pushUndo(); val c = Session.clips.removeAt(i); Session.clips.add(j, c); selectedClip = j; refreshTimeline()
    }

    private fun splitClip(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        if (c.isImage) { Toast.makeText(this, "Image split nahi hoti", Toast.LENGTH_SHORT).show(); return }
        pushUndo()
        val mid = (c.srcIn + c.srcOut) / 2.0
        val left = c.copy(srcOut = mid)
        val right = c.copy(srcIn = mid)
        Session.clips[i] = left; Session.clips.add(i + 1, right); refreshTimeline()
        Toast.makeText(this, "Clip do me split", Toast.LENGTH_SHORT).show()
    }

    private fun trimDialog(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 0) }
        if (c.isImage) {
            val dur = edit(c.imageDur.toString())
            box.addView(lbl("Image duration (sec)")); box.addView(dur)
            AlertDialog.Builder(this).setTitle("Trim image").setView(box)
                .setPositiveButton("OK") { _, _ -> pushUndo(); c.imageDur = dur.text.toString().toDoubleOrNull()?.coerceIn(0.3, 30.0) ?: c.imageDur; refreshTimeline() }
                .setNegativeButton("Cancel", null).show()
        } else {
            val si = edit(c.srcIn.toString()); val so = edit(c.srcOut.toString())
            box.addView(lbl("Start (sec) 0..${"%.1f".format(c.srcDur)}")); box.addView(si)
            box.addView(lbl("End (sec)")); box.addView(so)
            AlertDialog.Builder(this).setTitle("Trim clip ${i + 1}").setView(box)
                .setPositiveButton("OK") { _, _ ->
                    pushUndo()
                    val a = si.text.toString().toDoubleOrNull()?.coerceIn(0.0, c.srcDur - 0.2) ?: c.srcIn
                    val e = so.text.toString().toDoubleOrNull()?.coerceIn(a + 0.2, c.srcDur) ?: c.srcOut
                    c.srcIn = a; c.srcOut = e; refreshTimeline()
                }.setNegativeButton("Cancel", null).show()
        }
    }

    private fun speedDialog(i: Int) {
        val c = Session.clips.getOrNull(i) ?: return
        val speeds = listOf(0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
        val names = speeds.map { "${it}x" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Change speed")
            .setSingleChoiceItems(names, speeds.indexOf(c.speed).coerceAtLeast(2)) { d, w ->
                pushUndo(); c.speed = speeds[w]; refreshTimeline(); d.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    // ---- bottom toolbar panels ----
    private fun syncPreview() {
        val o = Session.opt
        b.ov.theme = Themes.byId(o.theme)
        b.ov.brand = o.brand; b.ov.tag = o.tag
        b.ov.popText = o.popText; b.ov.captionMargin = o.captionMargin
        b.ov.lang = o.lang; b.ov.fontChoice = o.fontChoice
        b.ov.highlightOverride = o.highlight; b.ov.textBg = o.textBg; b.ov.textBgColor = o.textBgColor
        b.ov.invalidate(); b.tl.musicOn = o.music; b.tl.invalidate()
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
            .setPositiveButton("Apply") { _, _ -> pushUndo(); onOk(); syncPreview() }.setNegativeButton("Cancel", null).show()
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
        show("Audio / Music", panel(swMusic, swVoice, lbl("Noise removal"), spDen, lbl("Pitch (-6..+6)"), sbPitch)) {
            Session.opt = o.copy(music = swMusic.isChecked, voiceClear = swVoice.isChecked,
                denoise = denoises[spDen.selectedItemPosition], pitch = (sbPitch.progress - 6).toDouble())
        }
    }

    /** Adjust: brightness / contrast / saturation on the selected clip (or all). */
    private fun adjustDialog() {
        val target = if (selectedClip in Session.clips.indices) selectedClip else -1
        val sbB = android.widget.SeekBar(this).apply { max = 40; progress = 20 }
        val sbC = android.widget.SeekBar(this).apply { max = 40; progress = 20 }
        val sbS = android.widget.SeekBar(this).apply { max = 40; progress = 20 }
        val title = if (target >= 0) "Adjust — clip ${target + 1}" else "Adjust — all clips"
        show(title, panel(lbl("Brightness"), sbB, lbl("Contrast"), sbC, lbl("Saturation"), sbS)) {
            val br = (sbB.progress - 20) / 100.0           // -0.2..+0.2
            val co = 0.6 + sbC.progress / 40.0 * 0.8        // 0.6..1.4
            val sa = 0.4 + sbS.progress / 40.0 * 1.2        // 0.4..1.6
            val eq = "eq=brightness=%.3f:contrast=%.3f:saturation=%.3f".format(br, co, sa)
            if (target >= 0) Session.clips[target].adjust = eq
            else Session.clips.forEach { it.adjust = eq }
        }
    }

    private val filterPresets = linkedMapOf(
        "None" to "", "Vivid" to "eq=saturation=1.4:contrast=1.12",
        "Warm" to "curves=r='0/0.05 1/1':b='0/0 1/0.92'", "Cool" to "curves=b='0/0.06 1/1':r='0/0 1/0.93'",
        "B&W" to "hue=s=0", "Vintage" to "curves=r='0/0.06 1/0.92':g='0/0.03 1/0.95':b='0/0.05 1/0.9',eq=contrast=0.95",
        "Film" to "eq=contrast=1.1:saturation=0.9,curves=b='0/0.08 1/0.95'")
    private fun filterDialog() {
        val target = if (selectedClip in Session.clips.indices) selectedClip else -1
        val names = filterPresets.keys.toTypedArray()
        val title = if (target >= 0) "Filter — clip ${target + 1}" else "Filter — all clips"
        AlertDialog.Builder(this).setTitle(title).setItems(names) { _, w ->
            pushUndo(); val fx = filterPresets.values.toList()[w]
            if (target >= 0) Session.clips[target].adjust = fx else Session.clips.forEach { it.adjust = fx }
            refreshTimeline(); Toast.makeText(this, "Filter: ${names[w]}", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    /** Frame: theme / background / b-roll / template / set-cover in one place. */
    private fun frameDialog() {
        val items = arrayOf("🎬 Transitions (clip ke beech)", "🎨 Theme", "🌀 Background (green-screen)", "🖼 Auto b-roll", "⚡ Template", "🖼 Set cover (poster)", "✨ AI auto-theme + pop-text")
        AlertDialog.Builder(this).setTitle("Frame / Style").setItems(items) { _, w ->
            when (w) { 0 -> transitionDialog(); 1 -> themeDialog(); 2 -> bgDialog(); 3 -> brollDialog(); 4 -> templateDialog(); 5 -> setCover(); 6 -> fxDialog() }
        }.setNegativeButton("Cancel", null).show()
    }
    private val transitions = linkedMapOf(
        "None (hard cut)" to "", "Fade" to "fade", "Fade black" to "fadeblack",
        "Dissolve" to "dissolve", "Slide left" to "slideleft", "Slide right" to "slideright",
        "Wipe left" to "wipeleft", "Circle open" to "circleopen", "Radial" to "radial")
    private fun transitionDialog() {
        val names = transitions.keys.toTypedArray()
        val cur = transitions.values.indexOf(Session.opt.transition).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Transitions (saari clips ke beech)")
            .setSingleChoiceItems(names, cur) { d, w ->
                pushUndo(); Session.opt = Session.opt.copy(transition = transitions.values.toList()[w]); d.dismiss()
                Toast.makeText(this, "Transition: ${names[w]}", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun setCover() {
        Session.coverSec = b.tl.currentTime()
        Toast.makeText(this, "Cover set @ %.1fs".format(Session.coverSec), Toast.LENGTH_SHORT).show()
    }
    private fun brollDialog() {
        val swAuto = swi("Auto b-roll images", Session.wantBroll)
        val swPip = swi("PiP (b-roll fullscreen + circle)", Session.opt.pip)
        val etPex = edit(Session.pexelsKey)
        show("B-roll", panel(swAuto, swPip, lbl("Pexels API key (optional)"), etPex)) {
            Session.wantBroll = swAuto.isChecked; Session.pexelsKey = etPex.text.toString()
            Session.opt = Session.opt.copy(pip = swPip.isChecked); refreshTimeline()
        }
    }
    private fun themeDialog() {
        val ids = Themes.ALL.map { it.label }
        val cur = Themes.ALL.indexOfFirst { it.id == Session.opt.theme }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(ids.toTypedArray(), cur) { d, w ->
            pushUndo(); Session.opt = Session.opt.copy(theme = Themes.ALL[w].id); syncPreview(); d.dismiss()
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
            pushUndo(); Session.opt = Templates.apply(Session.opt, w); syncPreview()
        }.setNegativeButton("Cancel", null).show()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var playing = false
    private var exported = false
    private fun fmt(sec: Double): String {
        val s = sec.toInt().coerceAtLeast(0); return "%02d:%02d".format(s / 60, s % 60)
    }
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!p.isPlaying) { playing = false; b.btnPlay.text = "▶"; return }
            if (!exported) { val t = composedTime(); b.ov.setTime(t); b.tl.seekTo(t); b.tvTime.text = "${fmt(t)} / ${fmt(Session.totalDur())}" }
            handler.postDelayed(this, 40)
        }
    }
    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause()
        else {
            if (!exported && p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0)
            p.play()
        }
    }

    private fun refreshTimeline(durIgnored: Double = 0.0) {
        val kws = Keywords.extract(Session.words)
        b.tl.captions = Session.words
        b.tl.clips = Session.clips
        if (selectedClip >= Session.clips.size) selectedClip = -1
        b.tl.selectedClip = selectedClip
        b.tl.broll = kws.map { Broll.Placed("", it.start, it.end) }.toMutableList()
        b.tl.musicOn = Session.opt.music
        b.tl.totalSec = Session.totalDur()
        b.tl.invalidate()
        b.ov.words = Session.words; b.ov.keywords = kws; b.ov.invalidate()
        if (player != null && !exported) buildPlaylist()
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
                pushUndo()
                val s = st.text.toString().toDoubleOrNull() ?: w.s
                val e = en.text.toString().toDoubleOrNull() ?: w.e
                Session.words[i] = Word(s, e, txt.text.toString())
                b.tl.invalidate(); b.ov.invalidate()
            }
            .setNeutralButton("Split") { _, _ ->
                pushUndo()
                val mid = (w.s + w.e) / 2
                val parts = w.w.split(" ")
                val a = if (parts.size > 1) parts.take(parts.size / 2).joinToString(" ") else w.w
                val bb = if (parts.size > 1) parts.drop(parts.size / 2).joinToString(" ") else w.w
                Session.words[i] = Word(w.s, mid, a)
                Session.words.add(i + 1, Word(mid, w.e, bb)); refreshTimeline()
            }
            .setNegativeButton("Delete") { _, _ -> pushUndo(); Session.words.removeAt(i); refreshTimeline() }
            .show()
    }

    private fun editBroll(i: Int) {
        val p = b.tl.broll.getOrNull(i) ?: return
        AlertDialog.Builder(this).setTitle("B-roll")
            .setMessage("Keyword b-roll at %.1f–%.1f s.\nImage auto aati hai (library/Pexels/AI). Keyword text badalna ho to Captions me shabd badlo."
                .format(p.start, p.end))
            .setPositiveButton("OK", null).show()
    }

    private fun export() {
        if (Session.clips.isEmpty()) { Toast.makeText(this, "Koi clip nahi", Toast.LENGTH_SHORT).show(); return }
        val words = Session.words.toList()
        val keywords = Keywords.extract(words)
        val res = if (b.spRes.selectedItemPosition == 0) 720 to 1280 else 1080 to 1920
        Session.opt = Session.opt.copy(outW = res.first, outH = res.second)
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
                // AI background-remove runs per the FIRST video clip only (talking-head)
                if (Session.bgMode == "ai") {
                    val idx = Session.clips.indexOfFirst { !it.isImage }
                    if (idx >= 0) {
                        val keyed = withContext(Dispatchers.IO) {
                            BgRemover.run(this@TimelineActivity, Session.clips[idx].path, Session.bgColor) { s, p -> runOnUiThread { setBusy(true, s, p) } }
                        }
                        val c = Session.clips[idx]; c.path = keyed; c.srcIn = 0.0; c.srcOut = durationSec(keyed); c.srcDur = c.srcOut
                    }
                }
                AutoEditEngine(this@TimelineActivity).renderMulti(Session.clips, words, Session.opt, broll, keywords,
                    object : AutoEditEngine.Listener {
                        override fun onProgress(pct: Int, step: String) = runOnUiThread { setBusy(true, step, pct) }
                        override fun onDone(o: String) = runOnUiThread {
                            outputPath = o; setBusy(false, "Export ready ✓", 100)
                            exported = true; playing = false; b.ov.visibility = android.view.View.GONE
                            player?.let { pl ->
                                pl.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(o))))
                                pl.prepare(); pl.playWhenReady = true
                            }
                            b.btnShare.isEnabled = true
                        }
                        override fun onError(m: String) = runOnUiThread { setBusy(false, "Error: $m", 0) }
                    })
            } catch (e: Exception) { runOnUiThread { setBusy(false, "Error: ${e.message}", 0) } }
        }
    }

    private fun setBusy(busy: Boolean, step: String, pct: Int) {
        b.btnSaveTop.isEnabled = !busy; b.progress.progress = pct; b.tvStatus.text = "$step  ($pct%)"
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
