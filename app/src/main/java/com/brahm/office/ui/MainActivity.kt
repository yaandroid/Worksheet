package com.brahm.office.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: FileAdapter
    private lateinit var empty: TextView
    private var tab = 0
    private var query = ""
    private var all: List<FileItem> = emptyList()

    // Permission is OPTIONAL now — only used to populate the "All files" tab.
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { reload() }

    // System file picker — needs NO storage permission, works on every device.
    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { openUri(it) } }

    // Folder picker — grants persistent access to list ALL files in a folder.
    private val openTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            com.brahm.office.util.TreeRepo.saveTree(this, uri)
            tab = 1
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        empty = findViewById(R.id.empty)
        val list = findViewById<RecyclerView>(R.id.list)

        // Clear status bar / nav bar (edge-to-edge on Android 15).
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, b.top, v.paddingRight, 0)
            insets
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(list) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, b.bottom)
            insets
        }
        list.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList()) { open(it) }
        list.adapter = adapter

        findViewById<TextInputEditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Tapping the empty-state: on "All files" tab pick a folder, else open a file.
        empty.setOnClickListener {
            if (tab == 1 && !hasFullAccess()) chooseFolder() else pickFile()
        }

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; refresh() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        // No forced redirect to settings. On old Android just ask for read (harmless).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open -> pickFile()
            R.id.action_new_sheet -> startActivity(Intent(this, DocActivity::class.java))
            R.id.action_choose_folder -> chooseFolder()
            R.id.action_all_files -> requestAllFilesAccess()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun pickFile() {
        val types = arrayOf(
            "application/pdf",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv", "text/comma-separated-values",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/octet-stream", "*/*"
        )
        try { openDoc.launch(types) } catch (e: Exception) { openDoc.launch(arrayOf("*/*")) }
    }

    private fun hasFullAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun chooseFolder() {
        try { openTree.launch(null) } catch (e: Exception) {}
    }

    /** Only when the user explicitly chooses it from the menu. */
    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {}
            }
        }
    }

    /** Copy a picked content Uri into cache and open the right screen. */
    private fun openUri(uri: Uri) {
        try {
            val name = queryName(uri)
            val out = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            Recents.add(this, out.absolutePath)
            routeFile(out.absolutePath, DocType.fromName(name))
        } catch (e: Exception) {
            empty.visibility = View.VISIBLE
            empty.text = "Could not open that file. Try another."
        }
    }

    private fun queryName(uri: Uri): String {
        var name = "document"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
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

    @Volatile private var scanning = false

    private fun reload() {
        if (tab != 1) { refresh(); return }   // only the "All files" tab scans
        val tree = com.brahm.office.util.TreeRepo.getTree(this)
        if (!hasFullAccess() && tree == null) { refresh(); return }  // show "choose folder" prompt
        if (scanning) return
        scanning = true
        if (all.isEmpty()) { empty.visibility = View.VISIBLE; empty.text = "Loading files…" }
        Thread {
            val result = try {
                if (hasFullAccess()) FileRepo.scan(this)
                else com.brahm.office.util.TreeRepo.scan(this, tree!!)
            } catch (e: Exception) { emptyList() }
            runOnUiThread { all = result; scanning = false; refresh() }
        }.start()
    }

    private fun refresh() {
        val base = when (tab) {
            0 -> Recents.get(this).mapNotNull { p ->
                val f = File(p)
                if (f.exists()) FileItem(f.name, p, f.length(), f.lastModified(), DocType.fromName(f.name)) else null
            }
            2 -> emptyList()
            else -> all
        }
        val filtered = if (query.isBlank()) base
            else base.filter { it.name.lowercase().contains(query.lowercase()) }

        when (tab) {
            2 -> {
                adapter.submit(emptyList())
                empty.visibility = View.VISIBLE
                empty.text = "PDF tools — open any PDF, then use the toolbar:\n\n• Merge  • Split  • Rotate\n• Delete page  • Extract range\n• Encrypt / decrypt  • Extract text\n\nTap here to open a file."
            }
            0 -> {
                adapter.submit(filtered)
                val none = filtered.isEmpty()
                empty.visibility = if (none) View.VISIBLE else View.GONE
                if (none) empty.text = "No recent files yet.\n\nTap here to open a PDF, Excel, Word or PPT — or open one from your file manager."
            }
            else -> {   // All files
                adapter.submit(filtered)
                val none = filtered.isEmpty()
                empty.visibility = if (none) View.VISIBLE else View.GONE
                val tree = com.brahm.office.util.TreeRepo.getTree(this)
                if (none) empty.text = if (!hasFullAccess() && tree == null)
                    "To list all your files here, tap to choose a folder\n(e.g. Download or Documents).\n\nYou pick it once — then every file in it shows up here, no permission needed."
                else "No documents found in the selected folder.\n\nTap to choose a different folder."
            }
        }
    }

    private fun open(f: FileItem) {
        if (f.path.startsWith("content://")) { openUri(Uri.parse(f.path)); return }
        Recents.add(this, f.path)
        routeFile(f.path, f.type)
    }

    private fun routeFile(path: String, type: DocType) {
        // Single unified surface (WebView engine): opens PDF, Excel, CSV, Word/PPT, HTML, MD, TXT.
        startActivity(Intent(this, DocActivity::class.java).putExtra("path", path))
    }
}
