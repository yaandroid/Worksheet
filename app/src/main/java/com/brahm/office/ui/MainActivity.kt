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
import com.brahm.office.util.FavRepo
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
    private lateinit var gridTiles: LinearLayout
    private lateinit var sectionLabel: TextView
    private lateinit var tabsView: TabLayout
    private var tab = 0
    private var query = ""
    private var all: List<FileItem> = emptyList()
    private var typeFilter = "ALL"
    private var favOnly = false
    private var sortMode = "DATE"

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { reload() }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openUri(it) } }
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            try {
                val result = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(res.data)
                result?.pdf?.uri?.let { openScannedPdf(it) }
            } catch (e: Exception) { android.widget.Toast.makeText(this, "Scan failed", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
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
        gridTiles = findViewById(R.id.gridTiles)
        sectionLabel = findViewById(R.id.sectionLabel)
        tabsView = findViewById(R.id.tabs)
        findViewById<View>(R.id.filterBtn).setOnClickListener { showFilterMenu(it) }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, b.top, v.paddingRight, 0); insets
        }

        list.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList(), { open(it) }, { count -> updateSelBar(count) })
        list.adapter = adapter

        list.isNestedScrollingEnabled = false
        val swipe = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe)
        swipe.setColorSchemeColors(Color.parseColor("#E8A33D"), Color.parseColor("#1F9D63"), Color.parseColor("#2B579A"))
        swipe.setOnRefreshListener { reload(); swipe.postDelayed({ swipe.isRefreshing = false }, 350) }

        findViewById<Button>(R.id.selShare).setOnClickListener { shareSelected() }
        findViewById<Button>(R.id.selStar).setOnClickListener { starSelected() }
        findViewById<Button>(R.id.selCancel).setOnClickListener { adapter.clearSelection() }

        buildTiles()

        findViewById<TextInputEditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        empty.setOnClickListener { if (tab == 1 && !hasFullAccess() && TreeRepo.getTree(this) == null) chooseFolder() else pickFile() }

        tabsView.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; updateSectionLabel(); reload() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
        updateSectionLabel()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() { super.onResume(); if (com.brahm.office.util.AppLock.guard(this)) return; reload() }

    // ---------- dashboard tile grid ----------
    private data class Tile(val icon: String, val label: String, val color: String, val action: String)
    private val tileDefs = listOf(
        Tile("📄", "PDF", "#D64541", "cat:PDF"),
        Tile("📊", "Excel", "#1F9D63", "cat:EXCEL"),
        Tile("📝", "Word", "#2B579A", "cat:WORD"),
        Tile("📽", "PPT", "#D24726", "cat:PPT"),
        Tile("🗂", "All files", "#5B6472", "type:ALL"),
        Tile("📂", "Open", "#E8A33D", "open"),
        Tile("➕", "New sheet", "#16233F", "new"),
        Tile("📷", "Scan", "#8E44AD", "scan")
    )
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    // Horizontal, scrollable row of tiles (standard-app look)
    private fun buildTiles() {
        gridTiles.removeAllViews()
        for (t in tileDefs) {
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = android.view.Gravity.CENTER
            tile.setPadding(dp(6), dp(8), dp(6), dp(8))

            val iconBox = TextView(this)
            iconBox.text = t.icon
            iconBox.textSize = 23f
            iconBox.gravity = android.view.Gravity.CENTER
            val d = android.graphics.drawable.GradientDrawable()
            d.cornerRadius = dp(18).toFloat()
            d.setColor(Color.parseColor(t.color))
            iconBox.background = d
            iconBox.layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))

            val lbl = TextView(this)
            lbl.text = t.label
            lbl.textSize = 11.5f
            lbl.gravity = android.view.Gravity.CENTER
            lbl.maxLines = 1
            lbl.setTextColor(Color.parseColor("#16233F"))
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp2.topMargin = dp(6)
            lbl.layoutParams = lp2

            tile.addView(iconBox); tile.addView(lbl)
            tile.setOnClickListener { onTile(t) }
            tile.isClickable = true
            tile.background = resolveRipple()

            val lp = LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            tile.layoutParams = lp
            gridTiles.addView(tile)
        }
    }
    // Filter dropdown: pick the file type to show on the All-files tab
    private fun showFilterMenu(anchor: View) {
        val pm = android.widget.PopupMenu(this, anchor)
        // itemId 0 = Favorites toggle; 1.. = type filters
        val favItem = pm.menu.add(0, 0, 0, "⭐ Favorites only"); favItem.isCheckable = true; favItem.isChecked = favOnly
        val opts = listOf("ALL" to "All files", "PDF" to "PDF", "EXCEL" to "Excel", "WORD" to "Word", "PPT" to "PowerPoint")
        for ((i, o) in opts.withIndex()) {
            val mi = pm.menu.add(1, i + 1, i + 1, o.second); mi.isCheckable = true; mi.isChecked = (typeFilter == o.first)
        }
        pm.setOnMenuItemClickListener { item ->
            if (item.itemId == 0) { favOnly = !favOnly }
            else { typeFilter = opts[item.itemId - 1].first }
            if (tab != 1) { tabsView.getTabAt(1)?.select() } else { updateSectionLabel(); reload() }
            true
        }
        pm.show()
    }
    private fun starSelected() {
        val sel = adapter.selectedItems(); if (sel.isEmpty()) return
        // if any selected isn't a favorite, star all; else un-star all (toggle on the group)
        val anyNotFav = sel.any { !FavRepo.isFav(this, it.path) }
        FavRepo.setFav(this, sel.map { it.path }, anyNotFav)
        android.widget.Toast.makeText(this, if (anyNotFav) "Added to favorites" else "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show()
        adapter.clearSelection(); reload()
    }
    private fun resolveRipple(): android.graphics.drawable.Drawable? {
        return try {
            val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
            val d = ta.getDrawable(0); ta.recycle(); d
        } catch (e: Exception) { null }
    }
    private fun onTile(t: Tile) {
        when {
            t.action.startsWith("cat:") -> startActivity(Intent(this, ToolsActivity::class.java).putExtra("category", t.action.substring(4)))
            t.action.startsWith("type:") -> {
                typeFilter = t.action.substring(5)
                tabsView.getTabAt(1)?.select()   // switch to All; its listener reloads
                tab = 1; updateSectionLabel(); reload()
            }
            t.action == "open" -> pickFile()
            t.action == "new" -> startActivity(Intent(this, DocActivity::class.java))
            t.action == "scan" -> launchScanner()
        }
    }
    private fun updateSectionLabel() {
        sectionLabel.text = when (tab) {
            1 -> (if (favOnly) "⭐ Favorites" else "All files") + (if (typeFilter != "ALL") " · ${typeFilter.lowercase().replaceFirstChar { it.uppercase() }}" else "")
            2 -> "Tools"
            else -> "Recent"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.main, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open -> pickFile()
            R.id.action_sort -> showSort()
            R.id.action_choose_folder -> chooseFolder()
            R.id.action_new_sheet -> startActivity(Intent(this, DocActivity::class.java))
            R.id.action_all_files -> requestAllFilesAccess()
            R.id.action_lock -> showLockSettings()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSort() {
        val opts = arrayOf("Date (newest)", "Name (A–Z)", "Size (largest)")
        AlertDialog.Builder(this).setTitle("Sort by").setItems(opts) { _, w ->
            sortMode = when (w) { 1 -> "NAME"; 2 -> "SIZE"; else -> "DATE" }; refresh()
        }.show()
    }

    // ---------- app lock (PIN / fingerprint) ----------
    private fun promptPin(title: String, onOk: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–12 digit PIN"
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ ->
                val p = input.text.toString()
                if (p.length < 4) android.widget.Toast.makeText(this, "PIN must be at least 4 digits", android.widget.Toast.LENGTH_SHORT).show()
                else onOk(p)
            }
            .setNegativeButton("Cancel", null).show()
    }
    private fun bioAvailable(): Boolean = androidx.biometric.BiometricManager.from(this)
        .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    private fun showLockSettings() {
        val lock = com.brahm.office.util.AppLock
        if (!lock.isEnabled(this)) {
            promptPin("Set an app-lock PIN") { pin ->
                promptPin("Confirm PIN") { again ->
                    if (again == pin) {
                        lock.setPin(this, pin)
                        if (bioAvailable()) {
                            AlertDialog.Builder(this).setTitle("Fingerprint unlock?")
                                .setMessage("Also unlock BRAHM Office with your fingerprint?")
                                .setPositiveButton("Yes") { _, _ -> lock.setBio(this, true); toastLock("App lock ON · PIN + fingerprint") }
                                .setNegativeButton("No, PIN only") { _, _ -> lock.setBio(this, false); toastLock("App lock ON · PIN only") }.show()
                        } else toastLock("App lock ON · PIN set")
                    } else android.widget.Toast.makeText(this, "PINs didn't match", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val bioOn = lock.useBio(this)
        val items = arrayOf("Change PIN", if (bioOn) "Turn OFF fingerprint" else "Turn ON fingerprint", "Remove app lock")
        AlertDialog.Builder(this).setTitle("App lock").setItems(items) { _, w ->
            when (w) {
                0 -> promptPin("New PIN") { pin -> promptPin("Confirm new PIN") { again -> if (again == pin) { lock.setPin(this, pin); toastLock("PIN changed") } else android.widget.Toast.makeText(this, "PINs didn't match", android.widget.Toast.LENGTH_SHORT).show() } }
                1 -> { if (!bioOn && !bioAvailable()) { android.widget.Toast.makeText(this, "No fingerprint set on this phone", android.widget.Toast.LENGTH_SHORT).show() } else { lock.setBio(this, !bioOn); toastLock(if (!bioOn) "Fingerprint ON" else "Fingerprint OFF") } }
                2 -> AlertDialog.Builder(this).setTitle("Remove app lock?").setMessage("Anyone will be able to open the app.")
                    .setPositiveButton("Remove") { _, _ -> lock.clear(this); toastLock("App lock removed") }
                    .setNegativeButton("Keep", null).show()
            }
        }.show()
    }
    private fun toastLock(m: String) = android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()

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
        val filtered = all.filter { typeMatch(it.type) && (!favOnly || FavRepo.isFav(this, it.path)) && (q.isBlank() || it.name.lowercase().contains(q)) }
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

    private fun launchScanner() {
        try {
            val opt = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(50)
                .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(opt)
                .getStartScanIntent(this)
                .addOnSuccessListener { sender -> scanLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e -> android.widget.Toast.makeText(this, "Scanner unavailable: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
        } catch (e: Exception) { android.widget.Toast.makeText(this, "Scanner not available on this device", android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun openScannedPdf(uri: Uri) {
        try {
            val out = File(cacheDir, "BRAHM-Scan-${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            Recents.add(this, out.absolutePath); routeFile(out.absolutePath)
        } catch (e: Exception) { android.widget.Toast.makeText(this, "Could not open scan", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun updateSelBar(count: Int) {
        val bar = findViewById<View>(R.id.selbar)
        if (count > 0) { bar.visibility = View.VISIBLE; findViewById<TextView>(R.id.selCount).text = "$count selected" }
        else bar.visibility = View.GONE
    }

    private fun shareItemUri(f: FileItem): Uri? = try {
        if (f.path.startsWith("content://")) Uri.parse(f.path)
        else androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", File(f.path))
    } catch (e: Exception) { null }

    private fun shareSelected() {
        val sel = adapter.selectedItems(); if (sel.isEmpty()) return
        val uris = ArrayList<Uri>(); sel.forEach { shareItemUri(it)?.let { u -> uris.add(u) } }
        if (uris.isEmpty()) { android.widget.Toast.makeText(this, "Could not share these files", android.widget.Toast.LENGTH_SHORT).show(); return }
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        intent.type = "*/*"; intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(intent, "Share ${uris.size} file(s)")); adapter.clearSelection() }
        catch (e: Exception) { android.widget.Toast.makeText(this, "No app to share with", android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun open(f: FileItem) {
        if (f.path.startsWith("content://")) { openUri(Uri.parse(f.path)); return }
        Recents.add(this, f.path); routeFile(f.path)
    }
    private fun routeFile(path: String) { startActivity(Intent(this, DocActivity::class.java).putExtra("path", path)) }

    override fun onBackPressed() {
        if (adapter.selectionMode) adapter.clearSelection() else super.onBackPressed()
    }
}
