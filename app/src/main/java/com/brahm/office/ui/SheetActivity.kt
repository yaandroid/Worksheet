package com.brahm.office.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brahm.office.R
import com.brahm.office.editor.PdfTools
import com.brahm.office.sheet.CellRef
import com.brahm.office.sheet.Sheet
import com.brahm.office.sheet.SpreadsheetView
import com.brahm.office.sheet.XlsxIO
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import kotlin.concurrent.thread

class SheetActivity : AppCompatActivity() {

    private lateinit var grid: SpreadsheetView
    private lateinit var cellRef: TextView
    private lateinit var input: EditText
    private var source: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sheet)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        grid = findViewById(R.id.grid)
        cellRef = findViewById(R.id.cellRef)
        input = findViewById(R.id.formulaInput)

        val path = intent.getStringExtra("path")
        source = path?.let { File(it) }
        supportActionBar?.title = source?.name ?: "New sheet"

        val sheet = Sheet()
        grid.sheet = sheet
        grid.onSelect = { r, c -> showCell(r, c) }

        source?.let { f ->
            if (f.exists()) {
                thread {
                    val loaded = try { XlsxIO.load(f) } catch (e: Exception) { Sheet() }
                    runOnUiThread { grid.sheet = loaded; grid.onSelect = { r, c -> showCell(r, c) }; showCell(0, 0) }
                }
            }
        }
        showCell(0, 0)

        findViewById<Button>(R.id.applyBtn).setOnClickListener { apply() }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { apply(); true } else false
        }
    }

    private fun showCell(r: Int, c: Int) {
        cellRef.text = CellRef.name(r, c)
        input.setText(grid.sheet.rawInput(r, c))
    }

    private fun apply() {
        val r = grid.selRow; val c = grid.selCol
        grid.sheet.set(r, c, input.text.toString())
        grid.selectCell(r + 1, c)   // move down like Excel
        grid.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sheet, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save_xlsx -> saveAs("xlsx")
            R.id.action_save_csv -> saveAs("csv")
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveAs(ext: String) {
        val dir = File(getExternalFilesDir(null), "BRAHM Office").apply { if (!exists()) mkdirs() }
        val base = source?.nameWithoutExtension ?: "sheet_${System.currentTimeMillis()}"
        val out = File(dir, "$base.$ext")
        try {
            if (ext == "csv") XlsxIO.saveCsv(grid.sheet, out) else XlsxIO.saveXlsx(grid.sheet, out)
            Toast.makeText(this, "Saved: ${out.name}", Toast.LENGTH_LONG).show()
            PdfTools.shareAny(this, out, if (ext == "csv") "text/csv"
                else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
