package com.brahm.autoeditor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brahm.autoeditor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Start screen: pick a raw video, transcribe, open the CapCut-style editor. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var inputPath: String? = null

    private val langCodes = listOf("en", "hi", "gu", "mr", "bn", "ta", "te", "kn", "ml", "pa")
    private val langNames = listOf("English", "Hindi", "Gujarati", "Marathi", "Bengali",
        "Tamil", "Telugu", "Kannada", "Malayalam", "Punjabi")

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        b.spLang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langNames)
        b.spLang.setSelection(langCodes.indexOf(prefs.lang).coerceAtLeast(0))
        b.etGroqKey.setText(prefs.groqKey)
        b.btnPick.setOnClickListener { pickVideo.launch("video/*") }
        b.btnCreate.setOnClickListener { create() }
    }

    private fun importVideo(uri: Uri) {
        val f = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
        contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        inputPath = f.absolutePath; b.tvFile.text = f.name
    }

    private fun durationSec(path: String): Double = try {
        val r = MediaMetadataRetriever(); r.setDataSource(path)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        r.release(); ms / 1000.0
    } catch (e: Exception) { 0.0 }

    private fun create() {
        val input = inputPath ?: run { Toast.makeText(this, "Pehle raw video chuno", Toast.LENGTH_SHORT).show(); return }
        prefs.lang = langCodes[b.spLang.selectedItemPosition]
        prefs.groqKey = b.etGroqKey.text.toString()
        val manual = b.etTranscript.text.toString()
        val apiKey = b.etGroqKey.text.toString()
        b.btnCreate.isEnabled = false; b.tvStatus.text = "Transcribing…"
        lifecycleScope.launch {
            try {
                val words = withContext(Dispatchers.IO) {
                    val tr = GroqTranscriber(this@MainActivity)
                    if (manual.isNotBlank()) tr.manual(manual, durationSec(input))
                    else tr.transcribe(input, apiKey, prefs.lang)
                }
                if (words.isEmpty()) throw RuntimeException("No speech / transcript.")
                Session.inputPath = input
                Session.words = words.toMutableList()
                Session.opt = AutoEditEngine.Options(lang = prefs.lang, captionMargin = 160)
                Session.groqKey = apiKey
                b.tvStatus.text = "Opening editor…"
                startActivity(android.content.Intent(this@MainActivity, TimelineActivity::class.java))
                b.btnCreate.isEnabled = true
            } catch (e: Exception) {
                runOnUiThread { b.btnCreate.isEnabled = true; b.tvStatus.text = "Error: ${e.message}" }
            }
        }
    }
}
