package com.brahm.office.util

import android.content.Context
import android.os.Environment
import java.io.File

/** Scans common storage locations for supported documents. */
object FileRepo {

    private val supported = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "xlsm", "csv", "tsv", "ppt", "pptx", "txt"
    )

    fun scan(context: Context, query: String = ""): List<FileItem> {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory())
            add(File(Environment.getExternalStorageDirectory(), "Download"))
            add(File(Environment.getExternalStorageDirectory(), "Documents"))
            context.getExternalFilesDir(null)?.let { add(it) }
        }.filter { it != null && it.exists() }

        val out = LinkedHashMap<String, FileItem>()
        val q = query.trim().lowercase()
        for (root in roots) walk(root!!, out, q, 0)
        return out.values.sortedByDescending { it.modified }
    }

    private const val MAX_RESULTS = 3000
    private val skipDirs = setOf("Android", "Download/.", "LOST.DIR", ".thumbnails")

    private fun walk(dir: File, out: MutableMap<String, FileItem>, q: String, depth: Int) {
        if (depth > 6 || out.size >= MAX_RESULTS) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (out.size >= MAX_RESULTS) return
            if (f.isHidden) continue
            if (f.isDirectory) {
                if (f.name in skipDirs) continue   // skip heavy/system dirs
                walk(f, out, q, depth + 1)
            } else {
                val ext = f.extension.lowercase()
                if (ext in supported && (q.isEmpty() || f.name.lowercase().contains(q))) {
                    out[f.absolutePath] = FileItem(
                        f.name, f.absolutePath, f.length(), f.lastModified(),
                        DocType.fromName(f.name)
                    )
                }
            }
        }
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.1f GB", mb / 1024.0)
    }
}
