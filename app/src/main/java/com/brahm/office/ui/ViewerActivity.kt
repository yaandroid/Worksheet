package com.brahm.office.ui

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.ScaleGestureDetector
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brahm.office.R
import com.brahm.office.editor.PdfTools
import com.brahm.office.pdf.PdfPageAdapter
import com.brahm.office.util.DocType
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

class ViewerActivity : AppCompatActivity() {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var adapter: PdfPageAdapter? = null
    private lateinit var pages: RecyclerView
    private lateinit var indicator: TextView
    private lateinit var message: TextView
    private var scale = 1f
    private var workingFile: File? = null
    private var type: DocType = DocType.OTHER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        pages = findViewById(R.id.pages)
        indicator = findViewById(R.id.pageIndicator)
        message = findViewById(R.id.message)
        pages.layoutManager = LinearLayoutManager(this)

        val file = resolveInput()
        if (file == null) {
            showMessage("Could not open this file.")
            return
        }
        workingFile = file
        type = DocType.fromName(file.name)
        supportActionBar?.title = file.name

        when (type) {
            DocType.PDF -> openPdf(file, null)
            DocType.TEXT -> showText(file)
            else -> showMessage(
                "${type.label} preview is being added in an upcoming build.\n\n" +
                "You can still share or convert this file from here."
            )
        }
        setupZoom()
    }

    /** Accept a direct path (from our browser) or an incoming VIEW/SEND Uri. */
    private fun resolveInput(): File? {
        intent.getStringExtra("path")?.let { return File(it) }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> intent.data
        }
        uri ?: return null
        return try {
            val name = queryName(uri)
            val out = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        } catch (e: Exception) { null }
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

    private fun openPdf(file: File, password: String?) {
        try {
            val target = if (password != null) {
                val dec = File(cacheDir, "dec_${file.name}")
                PdfTools.decrypt(file, password, dec); dec
            } else file
            pfd = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            val r = renderer!!
            adapter = PdfPageAdapter(r, scale) { pages.width }
            pages.adapter = adapter
            indicator.text = "1 / ${r.pageCount}"
            pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val pos = lm.findFirstVisibleItemPosition()
                    if (pos >= 0) indicator.text = "${pos + 1} / ${r.pageCount}"
                }
            })
        } catch (e: SecurityException) {
            promptPassword(file)
        } catch (e: Exception) {
            showMessage("This PDF could not be opened.\n${e.message ?: ""}")
        }
    }

    private fun promptPassword(file: File) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password"
        }
        AlertDialog.Builder(this)
            .setTitle("Protected PDF")
            .setMessage("This document is password-protected.")
            .setView(input)
            .setPositiveButton("Open") { _, _ -> openPdf(file, input.text.toString()) }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showText(file: File) {
        pages.visibility = RecyclerView.GONE
        message.visibility = TextView.VISIBLE
        message.movementMethod = ScrollingMovementMethod()
        message.text = try { file.readText() } catch (e: Exception) { "Could not read text." }
    }

    private fun showMessage(msg: String) {
        pages.visibility = RecyclerView.GONE
        indicator.visibility = TextView.GONE
        message.visibility = TextView.VISIBLE
        message.text = msg
    }

    private fun setupZoom() {
        val detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(0.5f, 4f)
                return true
            }
            override fun onScaleEnd(d: ScaleGestureDetector) {
                adapter?.setScale(scale)
            }
        })
        pages.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (e.pointerCount > 1) { detector.onTouchEvent(e); return true }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {
                detector.onTouchEvent(e)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (type == DocType.PDF) menuInflater.inflate(R.menu.viewer_pdf, menu)
        else menuInflater.inflate(R.menu.viewer_basic, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val f = workingFile ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.action_zoom_in -> { scale = (scale + 0.25f).coerceAtMost(4f); adapter?.setScale(scale) }
            R.id.action_zoom_out -> { scale = (scale - 0.25f).coerceAtLeast(0.5f); adapter?.setScale(scale) }
            R.id.action_share -> PdfTools.share(this, f)
            R.id.action_tools -> PdfToolsSheet.show(this, f) { reopen(it) }
            R.id.action_extract_text -> {
                val txt = PdfTools.extractText(f)
                val out = File(cacheDir, f.nameWithoutExtension + ".txt").apply { writeText(txt) }
                startActivity(Intent(this, ViewerActivity::class.java).putExtra("path", out.absolutePath))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reopen(newFile: File) {
        Toast.makeText(this, "Saved: ${newFile.name}", Toast.LENGTH_SHORT).show()
        closeRenderer()
        workingFile = newFile
        pages.adapter = null
        openPdf(newFile, null)
    }

    private fun closeRenderer() {
        try { adapter?.shutdown() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        renderer = null; pfd = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRenderer()
    }
}
