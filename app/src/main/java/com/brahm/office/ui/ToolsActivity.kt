package com.brahm.office.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.brahm.office.util.Recents
import java.io.ByteArrayOutputStream

/** A per-category tools screen (e.g. all PDF tools in one grid). Each tool opens a file (if needed) and auto-runs. */
class ToolsActivity : AppCompatActivity() {

    private data class Tool(val icon: String, val label: String, val color: String, val action: String)
    private var pendingAuto: String? = null
    private var pickKind = "pdf"

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val name = queryName(uri)
            val bos = ByteArrayOutputStream(); contentResolver.openInputStream(uri)?.use { it.copyTo(bos) }
            val safe = name.replace("/", "_")
            val f = java.io.File(cacheDir, "pick-" + System.currentTimeMillis() + "-" + safe); f.writeBytes(bos.toByteArray())
            Recents.add(this, f.absolutePath)
            val i = Intent(this, DocActivity::class.java).putExtra("path", f.absolutePath)
            if (pendingAuto != null) i.putExtra("autotool", pendingAuto)
            startActivity(i)
        } catch (e: Exception) { Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cat = intent.getStringExtra("category") ?: "PDF"
        val toolbar = com.google.android.material.appbar.MaterialToolbar(this)
        toolbar.title = catTitle(cat)
        toolbar.setBackgroundColor(Color.parseColor("#16233F"))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }
        setSupportActionBar(toolbar)

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#F3F5F9")); root.fitsSystemWindows = true
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this); scroll.isFillViewport = true
        val grid = GridLayout(this); grid.columnCount = 2
        grid.setPadding(dp(8), dp(10), dp(8), dp(16))
        for (t in toolsFor(cat)) grid.addView(buildCard(t, grid))
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun catTitle(c: String) = when (c) {
        "PDF" -> "PDF tools"; "EXCEL" -> "Excel"; "WORD" -> "Word"; "PPT" -> "PowerPoint"; else -> c
    }

    private fun toolsFor(cat: String): List<Tool> = when (cat) {
        "PDF" -> listOf(
            Tool("📂", "Open PDF", "#E8A33D", "open:pdf"),
            Tool("✏️", "Edit text", "#1F9D63", "tool:edit"),
            Tool("🔒", "Add password", "#2B579A", "tool:pwadd"),
            Tool("🔓", "Remove password", "#5B6472", "tool:pwremove"),
            Tool("📝", "PDF → Word (Hindi/OCR)", "#D24726", "tool:toword"),
            Tool("🖼", "PDF → Word (same look)", "#8E44AD", "tool:towordlook"),
            Tool("📄", "PDF → Text", "#2B579A", "tool:totext"),
            Tool("📤", "PDF → Images", "#1F9D63", "tool:toimages"),
            Tool("📑", "Organize pages", "#D64541", "tool:pages"),
            Tool("🔎", "Read text (OCR)", "#16A085", "tool:ocr")
        )
        "EXCEL" -> listOf(
            Tool("➕", "New spreadsheet", "#1F9D63", "new:sheet"),
            Tool("📂", "Open Excel", "#E8A33D", "open:excel")
        )
        "WORD" -> listOf(
            Tool("➕", "New Word doc", "#2B579A", "new:word"),
            Tool("📂", "Open Word", "#E8A33D", "open:word")
        )
        "PPT" -> listOf(
            Tool("📂", "Open PowerPoint", "#E8A33D", "open:ppt")
        )
        else -> emptyList()
    }

    private fun buildCard(t: Tool, parent: GridLayout): LinearLayout {
        val card = LinearLayout(this); card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable(); bg.cornerRadius = dp(16).toFloat(); bg.setColor(Color.WHITE)
        card.background = bg; card.elevation = dp(2).toFloat()
        card.setPadding(dp(14), dp(16), dp(12), dp(16))

        val icon = TextView(this); icon.text = t.icon; icon.textSize = 20f; icon.gravity = Gravity.CENTER
        val id = GradientDrawable(); id.cornerRadius = dp(12).toFloat(); id.setColor(Color.parseColor(t.color))
        icon.background = id
        val ilp = LinearLayout.LayoutParams(dp(44), dp(44)); ilp.marginEnd = dp(12); icon.layoutParams = ilp

        val lbl = TextView(this); lbl.text = t.label; lbl.textSize = 13.5f
        lbl.setTextColor(Color.parseColor("#16233F")); lbl.maxLines = 2
        lbl.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        card.addView(icon); card.addView(lbl)
        card.isClickable = true; card.foreground = ripple()
        card.setOnClickListener { onTool(t.action) }

        val glp = GridLayout.LayoutParams()
        glp.width = 0; glp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
        glp.setMargins(dp(6), dp(6), dp(6), dp(6))
        card.layoutParams = glp
        return card
    }

    private fun ripple() = try {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = ta.getDrawable(0); ta.recycle(); d
    } catch (e: Exception) { null }

    private fun onTool(action: String) {
        when {
            action.startsWith("new:") -> startActivity(Intent(this, DocActivity::class.java).putExtra("newdoc", action.substring(4)))
            action.startsWith("open:") -> { pendingAuto = null; pick(action.substring(5)) }
            action.startsWith("tool:") -> { pendingAuto = action.substring(5); pick("pdf") }
        }
    }

    private fun pick(kind: String) {
        val types = when (kind) {
            "pdf" -> arrayOf("application/pdf")
            "excel" -> arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values")
            "word" -> arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            "ppt" -> arrayOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            else -> arrayOf("*/*")
        }
        try { picker.launch(types) } catch (e: Exception) { Toast.makeText(this, "No file app", Toast.LENGTH_SHORT).show() }
    }

    private fun queryName(uri: android.net.Uri): String {
        var name = "document"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
        } catch (_: Exception) {}
        return name
    }
}
