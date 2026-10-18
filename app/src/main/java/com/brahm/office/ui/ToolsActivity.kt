package com.brahm.office.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
import com.brahm.office.util.DocType
import com.brahm.office.util.FileCache
import com.brahm.office.util.FileItem
import com.brahm.office.util.Recents
import java.io.ByteArrayOutputStream

/** A per-category screen: New + Open actions on top, and the list of that category's files below. */
class ToolsActivity : AppCompatActivity() {

    private data class Tool(val icon: String, val label: String, val color: String, val action: String)
    private var pendingAuto: String? = null
    private var category = "PDF"
    private lateinit var listHolder: LinearLayout

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
        category = intent.getStringExtra("category") ?: "PDF"
        val toolbar = com.google.android.material.appbar.MaterialToolbar(this)
        toolbar.title = catTitle(category)
        toolbar.setBackgroundColor(Color.parseColor("#16233F"))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_menu_revert)
        toolbar.setNavigationOnClickListener { finish() }
        setSupportActionBar(toolbar)

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#F3F5F9")); root.fitsSystemWindows = true
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this); scroll.isFillViewport = true
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL

        val grid = GridLayout(this); grid.columnCount = 2
        grid.setPadding(dp(8), dp(10), dp(8), dp(6))
        for (t in toolsFor(category)) grid.addView(buildCard(t, grid))
        col.addView(grid)

        listHolder = LinearLayout(this); listHolder.orientation = LinearLayout.VERTICAL
        listHolder.setPadding(dp(14), dp(6), dp(14), dp(16))
        col.addView(listHolder)

        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        buildFileList()
    }

    override fun onResume() {
        super.onResume()
        if (::listHolder.isInitialized) buildFileList()
    }

    private fun catTitle(c: String) = when (c) {
        "PDF" -> "PDF"; "EXCEL" -> "Excel"; "WORD" -> "Word"; "PPT" -> "PowerPoint"; else -> c
    }

    private fun typesFor(cat: String): Set<DocType> = when (cat) {
        "PDF" -> setOf(DocType.PDF)
        "EXCEL" -> setOf(DocType.EXCEL, DocType.CSV)
        "WORD" -> setOf(DocType.WORD, DocType.TEXT)
        "PPT" -> setOf(DocType.PPT)
        else -> emptySet()
    }

    private fun toolsFor(cat: String): List<Tool> = when (cat) {
        "PDF" -> listOf(
            Tool("➕", "New PDF", "#1F9D63", "new:pdf"),
            Tool("📂", "Open PDF", "#E8A33D", "open:pdf"),
            Tool("✏️", "Edit text", "#2B579A", "tool:edit"),
            Tool("🔒", "Add password", "#5B6472", "tool:pwadd"),
            Tool("🔓", "Remove password", "#8E44AD", "tool:pwremove"),
            Tool("📝", "PDF → Word (Hindi/OCR)", "#D24726", "tool:toword"),
            Tool("🖼", "PDF → Word (same look)", "#16A085", "tool:towordlook"),
            Tool("📄", "PDF → Text", "#2B579A", "tool:totext"),
            Tool("📤", "PDF → Images", "#1F9D63", "tool:toimages"),
            Tool("📑", "Organize pages", "#D64541", "tool:pages")
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

    // ---- category file list ----
    private fun buildFileList() {
        listHolder.removeAllViews()
        val wanted = typesFor(category)
        val items = try { FileCache.load(this) } catch (e: Exception) { emptyList<FileItem>() }
            .filter { it.type in wanted }
            .sortedByDescending { it.modified }
            .take(60)

        val hdr = TextView(this)
        hdr.text = "Recent ${catTitle(category)}" + if (items.isNotEmpty()) "  (${items.size})" else ""
        hdr.textSize = 14.5f; hdr.setTypeface(null, Typeface.BOLD)
        hdr.setTextColor(Color.parseColor("#16233F"))
        hdr.setPadding(dp(2), dp(6), dp(2), dp(8))
        listHolder.addView(hdr)

        if (items.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No ${catTitle(category)} files yet. Tap New or Open above."
            empty.textSize = 13f; empty.setTextColor(Color.parseColor("#8A93A3"))
            empty.setPadding(dp(2), dp(10), dp(2), dp(10))
            listHolder.addView(empty)
            return
        }
        for (f in items) listHolder.addView(buildFileRow(f))
    }

    private fun buildFileRow(f: FileItem): LinearLayout {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable(); bg.cornerRadius = dp(12).toFloat(); bg.setColor(Color.WHITE)
        row.background = bg; row.elevation = dp(1).toFloat()
        row.setPadding(dp(12), dp(12), dp(12), dp(12))

        val badge = TextView(this)
        badge.text = extOf(f.name)
        badge.textSize = 10f; badge.setTypeface(null, Typeface.BOLD); badge.gravity = Gravity.CENTER
        badge.setTextColor(Color.WHITE)
        val bd = GradientDrawable(); bd.cornerRadius = dp(9).toFloat(); bd.setColor(Color.parseColor(colorFor(f.type)))
        badge.background = bd
        val blp = LinearLayout.LayoutParams(dp(40), dp(40)); blp.marginEnd = dp(12); badge.layoutParams = blp

        val txt = LinearLayout(this); txt.orientation = LinearLayout.VERTICAL
        txt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val nm = TextView(this); nm.text = f.name; nm.textSize = 13.5f
        nm.setTextColor(Color.parseColor("#16233F")); nm.maxLines = 1; nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        val sub = TextView(this); sub.text = relTime(f.modified) + "  ·  " + humanSize(f.size)
        sub.textSize = 11.5f; sub.setTextColor(Color.parseColor("#8A93A3"))
        txt.addView(nm); txt.addView(sub)

        row.addView(badge); row.addView(txt)
        row.isClickable = true; row.foreground = ripple()
        row.setOnClickListener {
            try {
                Recents.add(this, f.path)
                startActivity(Intent(this, DocActivity::class.java).putExtra("path", f.path))
            } catch (e: Exception) { Toast.makeText(this, "Could not open", Toast.LENGTH_SHORT).show() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(4), 0, dp(4)); row.layoutParams = lp
        return row
    }

    private fun extOf(name: String): String {
        val i = name.lastIndexOf('.')
        val e = if (i in 1 until name.length - 1) name.substring(i + 1) else "FILE"
        return e.uppercase().take(4)
    }

    private fun colorFor(t: DocType) = when (t) {
        DocType.PDF -> "#D64541"; DocType.WORD -> "#2B579A"; DocType.EXCEL -> "#1F9D63"
        DocType.CSV -> "#16A085"; DocType.PPT -> "#D24726"; DocType.TEXT -> "#5B6472"; else -> "#8A93A3"
    }

    private fun humanSize(b: Long): String {
        if (b <= 0) return "0 B"
        val u = arrayOf("B", "KB", "MB", "GB"); var s = b.toDouble(); var i = 0
        while (s >= 1024 && i < u.size - 1) { s /= 1024; i++ }
        return (if (s >= 10 || i == 0) "%.0f".format(s) else "%.1f".format(s)) + " " + u[i]
    }

    private fun relTime(ms: Long): String {
        if (ms <= 0) return ""
        val d = System.currentTimeMillis() - ms
        val min = d / 60000; val hr = min / 60; val day = hr / 24
        return when {
            min < 1 -> "just now"; min < 60 -> "${min}m ago"; hr < 24 -> "${hr}h ago"
            day < 7 -> "${day}d ago"
            else -> android.text.format.DateFormat.format("dd MMM yyyy", ms).toString()
        }
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
