package com.brahm.office.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        empty = findViewById(R.id.empty)
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList()) { open(it) }
        list.adapter = adapter

        findViewById<TextInputEditText>(R.id.searchBox).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; refresh() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        findViewById<TabLayout>(R.id.tabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) { tab = t.position; refresh() }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })

        ensureAccess()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun ensureAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(i)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun reload() {
        all = try { FileRepo.scan(this) } catch (e: Exception) { emptyList() }
        refresh()
    }

    private fun refresh() {
        val base = when (tab) {
            0 -> {   // Recent
                val recent = Recents.get(this)
                recent.mapNotNull { p ->
                    val f = File(p)
                    if (f.exists()) FileItem(f.name, p, f.length(), f.lastModified(), DocType.fromName(f.name)) else null
                }
            }
            2 -> emptyList()      // Tools tab -> show tools grid instead
            else -> all
        }
        val filtered = if (query.isBlank()) base
            else base.filter { it.name.lowercase().contains(query.lowercase()) }

        if (tab == 2) {
            adapter.submit(emptyList())
            empty.visibility = View.VISIBLE
            empty.text = "Tools:\n\n• Merge PDFs\n• Split PDF\n• PDF → images\n• Images → PDF\n• Compress PDF\n• Encrypt / decrypt\n\nOpen any PDF, then use the editor toolbar."
        } else {
            adapter.submit(filtered)
            empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            if (filtered.isEmpty()) empty.setText(R.string.no_files)
        }
    }

    private fun open(f: FileItem) {
        Recents.add(this, f.path)
        when (f.type) {
            DocType.EXCEL, DocType.CSV ->
                startActivity(Intent(this, SheetActivity::class.java).putExtra("path", f.path))
            else ->
                startActivity(Intent(this, ViewerActivity::class.java).putExtra("path", f.path))
        }
    }
}
