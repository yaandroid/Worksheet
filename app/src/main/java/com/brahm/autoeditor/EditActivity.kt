package com.brahm.autoeditor

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.brahm.autoeditor.databinding.ActivityEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Edit screen: fix each word's TEXT and its START/END seconds before rendering,
 *  so nothing comes out wrong. Then Render & Preview. */
class EditActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditBinding
    private var outputPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etWords.setText(wordsToText(Session.words))
        b.tvHint.text = "Har line: start  end  text  —  galat shabd ya second yahin theek karo"
        b.btnRender.setOnClickListener { render() }
        b.btnShare.setOnClickListener { share() }
    }

    private fun wordsToText(ws: List<Word>): String =
        ws.joinToString("\n") { "%.2f  %.2f  %s".format(it.s, it.e, it.w) }

    private fun textToWords(t: String): List<Word> {
        val out = ArrayList<Word>()
        for (line in t.lines()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            val m = Regex("^([0-9]+\\.?[0-9]*)\\s+([0-9]+\\.?[0-9]*)\\s+(.+)$").find(s) ?: continue
            val a = m.groupValues[1].toDoubleOrNull() ?: continue
            val e = m.groupValues[2].toDoubleOrNull() ?: continue
            out.add(Word(a, e, m.groupValues[3].trim()))
        }
        return out
    }

    private fun render() {
        val words = textToWords(b.etWords.text.toString())
        if (words.isEmpty()) { toast("Kuch words chahiye"); return }
        Session.words = words.toMutableList()
        val input = Session.inputPath ?: run { toast("Video missing"); return }
        setBusy(true, "Starting…", 5)

        lifecycleScope.launch {
            try {
                val broll = if (Session.wantBroll) withContext(Dispatchers.IO) {
                    runOnUiThread { setBusy(true, "b-roll images…", 25) }
                    Broll.resolve(this@EditActivity, Keywords.extract(words), Session.pexelsKey, useAi = true)
                } else emptyList()

                var renderInput = input
                if (Session.wantBg) renderInput = withContext(Dispatchers.IO) {
                    BgRemover.run(this@EditActivity, input, Session.bgColor) { s, p ->
                        runOnUiThread { setBusy(true, s, p) }
                    }
                }

                AutoEditEngine(this@EditActivity).render(renderInput, words, Session.opt, broll,
                    object : AutoEditEngine.Listener {
                        override fun onProgress(pct: Int, step: String) = runOnUiThread { setBusy(true, step, pct) }
                        override fun onDone(outputPathArg: String) = runOnUiThread {
                            outputPath = outputPathArg
                            setBusy(false, "Preview ready ✓", 100)
                            b.vv.setVideoPath(outputPathArg); b.vv.start()
                            b.btnShare.isEnabled = true
                        }
                        override fun onError(message: String) = runOnUiThread { setBusy(false, "Error: $message", 0) }
                    })
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "Error: ${e.message}", 0) }
            }
        }
    }

    private fun setBusy(busy: Boolean, step: String, pct: Int) {
        b.btnRender.isEnabled = !busy
        b.progress.progress = pct
        b.tvStatus.text = "$step  ($pct%)"
    }

    private fun share() {
        val out = outputPath ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(out))
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = "video/mp4"; i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(android.content.Intent.createChooser(i, "Share reel"))
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
