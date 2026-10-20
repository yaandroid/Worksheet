package com.brahm.autoeditor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.brahm.autoeditor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var inputPath: String? = null
    private var outputPath: String? = null

    private val langCodes = listOf("en", "hi", "gu", "mr", "bn", "ta", "te", "kn", "ml", "pa")
    private val langNames = listOf("English", "Hindi", "Gujarati", "Marathi", "Bengali",
        "Tamil", "Telugu", "Kannada", "Malayalam", "Punjabi")
    private val denoiseVals = listOf("off", "light", "medium", "strong")

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        b.spTheme.adapter = adapter(Themes.ALL.map { it.label })
        b.spLang.adapter = adapter(langNames)
        b.spDenoise.adapter = adapter(denoiseVals.map { it.replaceFirstChar { c -> c.uppercase() } })
        b.spDenoise.setSelection(2)
        b.spBg.adapter = adapter(listOf("None", "Green screen (fast)", "AI remove (slow)"))
        b.etBrand.setText(prefs.brand)
        b.etGroqKey.setText(prefs.groqKey)
        b.etPexels.setText(prefs.pexelsKey)
        b.spLang.setSelection(langCodes.indexOf(prefs.lang).coerceAtLeast(0))
        b.spTheme.setSelection(Themes.ALL.indexOfFirst { it.id == prefs.theme }.coerceAtLeast(0))

        b.sbPitch.max = 12; b.sbPitch.progress = 6; b.tvPitch.text = "0"
        b.sbPitch.setOnSeekBarChangeListenerSimple { b.tvPitch.text = (it - 6).toString() }

        b.btnPick.setOnClickListener { pickVideo.launch("video/*") }
        b.btnGo.setOnClickListener { start() }
        b.btnShare.setOnClickListener { shareOutput() }
    }

    private fun adapter(items: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private fun importVideo(uri: Uri) {
        val f = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
        contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        inputPath = f.absolutePath
        b.tvFile.text = f.name
        Toast.makeText(this, "Video ready", Toast.LENGTH_SHORT).show()
    }

    private fun durationSec(path: String): Double = try {
        val r = MediaMetadataRetriever(); r.setDataSource(path)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        r.release(); ms / 1000.0
    } catch (e: Exception) { 0.0 }

    private fun start() {
        val input = inputPath ?: run {
            Toast.makeText(this, "Pehle raw video daalo", Toast.LENGTH_SHORT).show(); return
        }
        prefs.brand = b.etBrand.text.toString()
        prefs.groqKey = b.etGroqKey.text.toString()
        prefs.pexelsKey = b.etPexels.text.toString()
        prefs.theme = Themes.ALL[b.spTheme.selectedItemPosition].id
        prefs.lang = langCodes[b.spLang.selectedItemPosition]

        val opt = AutoEditEngine.Options(
            theme = prefs.theme, lang = prefs.lang,
            denoise = denoiseVals[b.spDenoise.selectedItemPosition],
            voiceClear = b.swVoice.isChecked,
            pitch = (b.sbPitch.progress - 6).toDouble(),
            brand = b.etBrand.text.toString().ifBlank { "BRAHM" },
            tag = b.etTag.text.toString().ifBlank { "HOMEOPATHY" },
            music = b.swMusic.isChecked,
            bgMode = if (b.spBg.selectedItemPosition == 1) "green" else "none",
            bgColor = b.etBgColor.text.toString().ifBlank { "#0a1a33" })
        val manual = b.etTranscript.text.toString()
        val apiKey = b.etGroqKey.text.toString()
        setBusy(true, "Transcribing…", 20)

        lifecycleScope.launch {
            try {
                val words = withContext(Dispatchers.IO) {
                    val tr = GroqTranscriber(this@MainActivity)
                    if (manual.isNotBlank()) tr.manual(manual, durationSec(input))
                    else tr.transcribe(input, apiKey, prefs.lang)
                }
                if (words.isEmpty()) throw RuntimeException("No speech / transcript.")
                // hand off to the Edit & Preview screen (fix text/timing, then render)
                Session.inputPath = input
                Session.words = words.toMutableList()
                Session.opt = opt
                Session.wantBroll = b.swBroll.isChecked
                Session.bgMode = when (b.spBg.selectedItemPosition) { 1 -> "green"; 2 -> "ai"; else -> "none" }
                Session.wantAutoTheme = b.swAutoTheme.isChecked
                Session.pexelsKey = b.etPexels.text.toString()
                Session.groqKey = apiKey
                Session.bgColor = b.etBgColor.text.toString().ifBlank { "#0a1a33" }
                setBusy(false, "Edit screen…", 100)
                startActivity(android.content.Intent(this@MainActivity, EditActivity::class.java))
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "Error: ${e.message}", 0) }
            }
        }
    }

    private fun setBusy(busy: Boolean, step: String, pct: Int) {
        b.btnGo.isEnabled = !busy
        b.progress.progress = pct
        b.tvStatus.text = "$step  ($pct%)"
    }

    private fun shareOutput() {
        val out = outputPath ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", File(out))
        val i = android.content.Intent(android.content.Intent.ACTION_SEND)
        i.type = "video/mp4"; i.putExtra(android.content.Intent.EXTRA_STREAM, uri)
        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(android.content.Intent.createChooser(i, "Share reel"))
    }
}
