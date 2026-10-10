package com.brahm.office.ui

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.brahm.office.editor.PdfTools
import java.io.File

/** Simple tool picker for PDF editing operations. */
object PdfToolsSheet {

    fun show(activity: Activity, file: File, onSaved: (File) -> Unit) {
        val tools = arrayOf(
            "Rotate all pages 90°",
            "Rotate one page",
            "Delete a page",
            "Extract page range",
            "Split every page",
            "Encrypt with password",
            "Share"
        )
        AlertDialog.Builder(activity)
            .setTitle("PDF tools")
            .setItems(tools) { _, which ->
                when (which) {
                    0 -> run(activity, onSaved) { out -> PdfTools.rotateAll(file, 90, out); out }
                    1 -> askNumber(activity, "Page number to rotate") { p ->
                        run(activity, onSaved) { out -> PdfTools.rotatePage(file, p - 1, 90, out); out }
                    }
                    2 -> askNumber(activity, "Page number to delete") { p ->
                        run(activity, onSaved) { out -> PdfTools.deletePage(file, p - 1, out); out }
                    }
                    3 -> askRange(activity) { a, b ->
                        run(activity, onSaved) { out -> PdfTools.extractRange(file, a, b, out); out }
                    }
                    4 -> {
                        val dir = outDir(activity)
                        val parts = PdfTools.splitEachPage(file, dir)
                        Toast.makeText(activity, "Split into ${parts.size} files in ${dir.name}", Toast.LENGTH_LONG).show()
                    }
                    5 -> askPassword(activity) { pwd ->
                        run(activity, onSaved) { out -> PdfTools.encrypt(file, pwd, pwd, out); out }
                    }
                    6 -> PdfTools.share(activity, file)
                }
            }
            .show()
    }

    private fun run(activity: Activity, onSaved: (File) -> Unit, op: (File) -> File) {
        try {
            val out = File(outDir(activity), "edited_${System.currentTimeMillis()}.pdf")
            op(out)
            onSaved(out)
        } catch (e: Exception) {
            Toast.makeText(activity, "Operation failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun outDir(activity: Activity): File {
        val d = File(activity.getExternalFilesDir(null), "BRAHM Office")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun askNumber(activity: Activity, title: String, cb: (Int) -> Unit) {
        val input = EditText(activity).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(activity).setTitle(title).setView(input)
            .setPositiveButton("OK") { _, _ ->
                input.text.toString().toIntOrNull()?.let { cb(it) }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun askRange(activity: Activity, cb: (Int, Int) -> Unit) {
        val from = EditText(activity).apply { hint = "From page"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val to = EditText(activity).apply { hint = "To page"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0); addView(from); addView(to)
        }
        AlertDialog.Builder(activity).setTitle("Extract page range").setView(box)
            .setPositiveButton("OK") { _, _ ->
                val a = from.text.toString().toIntOrNull() ?: 1
                val b = to.text.toString().toIntOrNull() ?: a
                cb(a, b)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun askPassword(activity: Activity, cb: (String) -> Unit) {
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password"
        }
        AlertDialog.Builder(activity).setTitle("Set password").setView(input)
            .setPositiveButton("Encrypt") { _, _ ->
                val p = input.text.toString()
                if (p.isNotEmpty()) cb(p)
            }.setNegativeButton("Cancel", null).show()
    }
}
