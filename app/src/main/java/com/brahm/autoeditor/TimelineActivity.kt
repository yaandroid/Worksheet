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
        b.tl.onSeek = { t -> if (!exported) { b.vv.seekTo((t * 1000).toInt()); b.ov.setTime(t) } }

        b.tl.onSelect = { track, index -> if (track == 1) editCaption(index) else editBroll(index) }
        b.tl.onEdited = { b.ov.invalidate() }
        b.btnZoomIn.setOnClickListener { b.tl.zoomIn() }
        b.btnZoomOut.setOnClickListener { b.tl.zoomOut() }
        b.btnPlay.setOnClickListener { togglePlay() }
        b.btnExport.setOnClickListener { export() }
        b.btnShare.setOnClickListener { share() }
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
