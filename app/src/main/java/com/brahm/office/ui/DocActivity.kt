package com.brahm.office.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.brahm.office.util.DocType
import java.io.ByteArrayOutputStream

/** Single document surface: hosts the BRAHM Office web engine in a WebView. */
class DocActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pageReady = false
    private var pendingB64: String? = null
    private var pendingName: String? = null

    // Pending bytes to write for a save
    private var saveBytes: ByteArray? = null

    private val openPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { feedUri(it) }
    }
    // Picks a file and hands its bytes to the web layer WITHOUT opening it (for merge / image→pdf).
    private val pickForWeb = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = queryName(uri)
            val bos = ByteArrayOutputStream(); contentResolver.openInputStream(uri)?.use { it.copyTo(bos) }
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            val safe = name.replace("\\", "").replace("\"", "")
            runOnUiThread { web.evaluateJavascript("window.BrahmPicked && window.BrahmPicked(\"$b64\",\"$safe\");", null) }
        } catch (e: Exception) { Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show() }
    }
    private val savePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val bytes = saveBytes; saveBytes = null
        if (uri != null && bytes != null) {
            try { contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Save failed", Toast.LENGTH_LONG).show() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Wrap the WebView in a parent and pad THAT for system bars (WebView eats its own insets).
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(0xFF16233F.toInt())
        web = WebView(this)
        root.addView(web, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        // Android 15 (targetSdk 35) draws edge-to-edge: pad the WebView by the
        // system bar insets so the UI never sits under the status / nav bars.
        web.setBackgroundColor(0xFF16233F.toInt())  // navy — matches top & bottom bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
            builtInZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = false
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        // Enables JS alert/confirm/prompt dialogs (used for rename/delete sheet).
        web.webChromeClient = android.webkit.WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                flushPending()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean = false
        }
        web.loadUrl("file:///android_asset/webapp/index.html")

        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onResume() { super.onResume(); com.brahm.office.util.AppLock.guard(this) }

    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("path")?.let { path ->
            try { feedBytes(java.io.File(path).readBytes(), java.io.File(path).name) } catch (_: Exception) {}
            return
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        uri?.let { feedUri(it) }
    }

    private fun feedUri(uri: Uri) {
        try {
            val name = queryName(uri)
            val bos = ByteArrayOutputStream()
            contentResolver.openInputStream(uri)?.use { it.copyTo(bos) }
            feedBytes(bos.toByteArray(), name)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file", Toast.LENGTH_LONG).show()
        }
    }

    private fun feedBytes(bytes: ByteArray, name: String) {
        pendingB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        pendingName = name
        if (pageReady) flushPending()
    }

    private fun flushPending() {
        val b64 = pendingB64 ?: return
        val name = (pendingName ?: "document").replace("\\", "").replace("\"", "")
        pendingB64 = null; pendingName = null
        runOnUiThread {
            web.evaluateJavascript("window.BrahmOpen(\"$b64\",\"$name\");", null)
        }
    }

    private fun queryName(uri: Uri): String {
        var name = "document"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
        } catch (_: Exception) {}
        if (!name.contains('.')) {
            name += when (DocType.fromMime(contentResolver.getType(uri))) {
                DocType.PDF -> ".pdf"; DocType.EXCEL -> ".xlsx"; DocType.WORD -> ".docx"
                DocType.PPT -> ".pptx"; DocType.CSV -> ".csv"; else -> ".txt"
            }
        }
        return name
    }

    inner class Bridge {
        @JavascriptInterface fun ready() {}
        @JavascriptInterface fun pickFile() {
            runOnUiThread {
                try { openPicker.launch(arrayOf("*/*")) } catch (_: Exception) {}
            }
        }
        /** Pick a file and return its bytes to window.BrahmPicked (does NOT open it). kind: "pdf" | "image" | "any" */
        @JavascriptInterface fun pickFor(kind: String) {
            val types = when (kind) {
                "pdf" -> arrayOf("application/pdf")
                "image" -> arrayOf("image/*")
                else -> arrayOf("*/*")
            }
            runOnUiThread { try { pickForWeb.launch(types) } catch (_: Exception) {} }
        }
        @JavascriptInterface fun saveFile(name: String, mime: String, base64: String) {
            try { saveBytes = Base64.decode(base64, Base64.DEFAULT) } catch (_: Exception) { return }
            runOnUiThread {
                try { savePicker.launch(name) } catch (_: Exception) {}
            }
        }
        /** Share the current document bytes via the Android share sheet. */
        @JavascriptInterface fun shareBytes(name: String, mime: String, base64: String) {
            try {
                val data = Base64.decode(base64, Base64.DEFAULT)
                val safe = name.replace("/", "_").replace("\\", "_")
                val dir = java.io.File(cacheDir, "share"); dir.mkdirs()
                val f = java.io.File(dir, if (safe.isBlank()) "document" else safe); f.writeBytes(data)
                val uri = androidx.core.content.FileProvider.getUriForFile(this@DocActivity, "$packageName.fileprovider", f)
                val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                runOnUiThread { try { startActivity(Intent.createChooser(i, "Share")) } catch (_: Exception) {} }
            } catch (_: Exception) {}
        }
        /** On-device OCR of a PNG (base64). lang: "hi" → Devanagari, else Latin. */
        @JavascriptInterface fun ocr(base64Png: String, lang: String) {
            try {
                val data = Base64.decode(base64Png, Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bmp == null) { ocrBack("", "decode failed"); return }
                val img = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)
                val rec = if (lang == "hi")
                    com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions.Builder().build())
                else
                    com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                rec.process(img)
                    .addOnSuccessListener { r -> ocrBack(r.text, "") }
                    .addOnFailureListener { e -> ocrBack("", e.message ?: "ocr failed") }
            } catch (e: Exception) { ocrBack("", e.message ?: "error") }
        }
    }

    private fun ocrBack(text: String, err: String) {
        val t = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val e = err.replace("\\", "").replace("\"", "")
        runOnUiThread { web.evaluateJavascript("window.BrahmOcrResult(\"$t\",\"$e\");", null) }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
