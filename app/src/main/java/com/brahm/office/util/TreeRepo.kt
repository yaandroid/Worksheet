package com.brahm.office.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Lists supported documents inside a user-granted folder tree (SAF).
 * Needs NO storage permission — works on every device.
 */
object TreeRepo {

    private const val PREF = "brahm_tree"
    private const val KEY = "uri"
    private val supported = setOf("pdf", "doc", "docx", "xls", "xlsx", "xlsm", "csv", "tsv", "ppt", "pptx", "txt")

    fun saveTree(context: Context, uri: Uri) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, uri.toString()).apply()
    }

    fun getTree(context: Context): Uri? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try { Uri.parse(s) } catch (e: Exception) { null }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun scan(context: Context, treeUri: Uri, query: String = ""): List<FileItem> {
        val root = try { DocumentFile.fromTreeUri(context, treeUri) } catch (e: Exception) { null } ?: return emptyList()
        val out = ArrayList<FileItem>()
        val q = query.trim().lowercase()
        walk(root, out, q, 0)
        return out.sortedByDescending { it.modified }
    }

    private fun walk(dir: DocumentFile, out: MutableList<FileItem>, q: String, depth: Int) {
        if (depth > 8 || out.size >= 5000) return
        val children = try { dir.listFiles() } catch (e: Exception) { return }
        for (f in children) {
            if (out.size >= 5000) return
            if (f.isDirectory) {
                walk(f, out, q, depth + 1)
            } else {
                val name = f.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supported && (q.isEmpty() || name.lowercase().contains(q))) {
                    out.add(FileItem(name, f.uri.toString(), f.length(), f.lastModified(), DocType.fromName(name)))
                }
            }
        }
    }
}
