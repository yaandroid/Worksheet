package com.brahm.office.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brahm.office.R
import com.brahm.office.files.FileAdapter
import com.brahm.office.util.DocType
import com.brahm.office.util.FileItem
import com.brahm.office.util.FileRepo
import com.brahm.office.util.Recents
import com.brahm.office.util.TreeRepo
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: FileAdapter
    private lateinit var empty: TextView
    private lateinit var chipsBox: LinearLayout
    private lateinit var chipsScroll: View
    private var tab = 0
    private var query = ""
    private var all: List<FileItem> = emptyList()
    private var typeFilter = "ALL"
    private var sortMode = "DATE"

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openUri(it) } }
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
            TreeRepo.saveTree(this, uri); tab = 1; reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        empty = findViewById(R.id.empty)
        val list = findViewById<RecyclerView>(R.id.list)
        chipsBox = findViewById(R.id.chips)
        chipsScroll = findViewById(R.id.chipsScroll)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, b.top, v.paddingRight, 0); insets
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, b.bottom); insets
        }

        list.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList()) { open(it) }
        list.adapter = adapter

        buildChips()

        findViewById<TextInputEditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        empty.setOnClickListener { if (tab == 1 && !hasFullAccess() && TreeRepo.getTree(this) == null) chooseFolder() else pickFile() }

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; updateChipsVisibility(); reload() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        updateChipsVisibility()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() { super.onResume(); reload() }

    // ---------- chips ----------
    private val chipDefs = listOf("ALL" to "All", "PDF" to "PDF", "EXCEL" to "Excel", "WORD" to "Word", "PPT" to "PPT")
    private fun buildChips() {
        chipsBox.removeAllViews()
        for ((key, label) in chipDefs) {
            val b = Button(this)
            b.text = label
            b.isAllCaps = false
            b.setPadding(28, 6, 28, 6)
            b.minWidth = 0; b.minHeight = 0
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = 10; b.layoutParams = lp
            styleChip(b, key == typeFilter)
            b.setOnClickListener { typeFilter = key; buildChips(); refresh() }
            chipsBox.addView(b)
        }
    }
    private fun styleChip(b: Button, active: Boolean) {
        if (active) { b.setBackgroundColor(Color.parseColor("#E8A33D")); b.setTextColor(Color.parseColor("#2a1d00")) }
        else { b.setBackgroundColor(Color.parseColor("#24324f")); b.setTextColor(Color.WHITE) }
    }
    private fun updateChipsVisibility() { chipsScroll.visibility = if (tab == 1) View.VISIBLE else View.GONE }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.main, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open -> pickFile()
            R.id.action_sort -> showSort()
            R.id.action_choose_folder -> chooseFolder()
            R.id.action_new_sheet -> startActivity(Intent(this, DocActivity::class.java))
            R.id.action_all_files -> requestAllFilesAccess()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSort() {
        val opts = arrayOf("Date (newest)", "Name (A–Z)", "Size (largest)")
        AlertDialog.Builder(this).setTitle("Sort by").setItems(opts) { _, w ->
            sortMode = when (w) { 1 -> "NAME"; 2 -> "SIZE"; else -> "DATE" }; refresh()
        }.show()
    }

    private fun pickFile() {
        val types = arrayOf("application/pdf","application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","text/csv",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain","application/octet-stream","*/*")
        try { openDoc.launch(types) } catch (e: Exception) { openDoc.launch(arrayOf("*/*")) }
    }
    private fun chooseFolder() { try { openTree.launch(null) } catch (e: Exception) {} }
    private fun hasFullAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {} }
        }
    }

    private fun openUri(uri: Uri) {
        try {
            val name = queryName(uri)
            val out = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            Recents.add(this, out.absolutePath); routeFile(out.absolutePath)
        } catch (e: Exception) { empty.visibility = View.VISIBLE; empty.text = "Could not open that file." }
    }
    private fun queryName(uri: Uri): String {
        var name = "document"
        try { contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) } } catch (_: Exception) {}
        if (!name.contains('.')) name += when (DocType.fromMime(contentResolver.getType(uri))) {
            DocType.PDF -> ".pdf"; DocType.EXCEL -> ".xlsx"; DocType.WORD -> ".docx"; DocType.PPT -> ".pptx"; DocType.CSV -> ".csv"; else -> ".txt" }
        return name
    }

    @Volatile private var scanning = false
    private fun reload() {
        if (tab != 1) { refresh(); return }
        val tree = TreeRepo.getTree(this)
        if (!hasFullAccess() && tree == null) { refresh(); return }
        if (scanning) return
        scanning = true
        if (all.isEmpty()) { empty.visibility = View.VISIBLE; empty.text = "Loading files…" }
        Thread {
            val result = try { if (hasFullAccess()) FileRepo.scan(this) else TreeRepo.scan(this, tree!!) } catch (e: Exception) { emptyList() }
            runOnUiThread { all = result; scanning = false; refresh() }
        }.start()
    }

    private fun typeMatch(t: DocType): Boolean = when (typeFilter) {
        "ALL" -> true; "PDF" -> t == DocType.PDF; "EXCEL" -> t == DocType.EXCEL || t == DocType.CSV
        "WORD" -> t == DocType.WORD; "PPT" -> t == DocType.PPT; else -> true
    }

    private fun sortFiles(l: List<FileItem>): List<FileItem> = when (sortMode) {
        "NAME" -> l.sortedBy { it.name.lowercase() }
        "SIZE" -> l.sortedByDescending { it.size }
        else -> l.sortedByDescending { it.modified }
    }

    private fun refresh() {
        when (tab) {
            2 -> {
                adapter.submit(emptyList()); empty.visibility = View.VISIBLE
                empty.text = "PDF tools — open any PDF, then use the toolbar:\n\n• Zoom (double-tap)  • Magnifier (long-press)\n\nTap here to open a file."
                return
            }
            0 -> {
                val recent = Recents.get(this).mapNotNull { p ->
                    val f = File(p); if (f.exists()) FileItem(f.name, p, f.length(), f.lastModified(), DocType.fromName(f.name)) else null }
                val filtered = recent.filter { (query.isBlank() || it.name.lowercase().contains(query.lowercase())) }
                adapter.submit(filtered)
                empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                if (filtered.isEmpty()) empty.text = "No recent files yet.\n\nTap here to open a PDF, Excel, Word or PPT — or open one from your file manager."
                return
            }
        }
        // ---- All files: grouped by folder ----
        val q = query.lowercase()
        val filtered = all.filter { typeMatch(it.type) && (q.isBlank() || it.name.lowercase().contains(q)) }
        if (filtered.isEmpty()) {
            adapter.submit(emptyList()); empty.visibility = View.VISIBLE
            val tree = TreeRepo.getTree(this)
            empty.text = if (!hasFullAccess() && tree == null)
                "To list all your files here, tap to choose a folder\n(e.g. Download or Documents).\n\nYou pick it once — every file in it shows up here, grouped by folder. No permission needed."
            else "No documents found.\n\nTap to choose a different folder."
            return
        }
        empty.visibility = View.GONE
        val groups = filtered.groupBy { if (it.folder.isBlank()) "Other" else it.folder }
        // order groups by most-recent file inside
        val orderedKeys = groups.keys.sortedByDescending { k -> groups[k]!!.maxOf { it.modified } }
        val out = ArrayList<Any>()
        for (k in orderedKeys) { out.add("📁  $k"); out.addAll(sortFiles(groups[k]!!)) }
        adapter.submit(out)
    }

    private fun open(f: FileItem) {
        if (f.path.startsWith("content://")) { openUri(Uri.parse(f.path)); return }
        Recents.add(this, f.path); routeFile(f.path)
    }
    private fun routeFile(path: String) { startActivity(Intent(this, DocActivity::class.java).putExtra("path", path)) }
}
