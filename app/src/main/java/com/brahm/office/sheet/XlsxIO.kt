package com.brahm.office.sheet

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.reader.ReadableWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** xlsx / csv read + write for the spreadsheet engine. */
object XlsxIO {

    fun load(file: File): Sheet {
        return if (file.extension.lowercase() in listOf("csv", "tsv")) loadCsv(file)
        else loadXlsx(file)
    }

    private fun loadXlsx(file: File): Sheet {
        val sheet = Sheet(file.nameWithoutExtension)
        FileInputStream(file).use { fis ->
            ReadableWorkbook(fis).use { wb ->
                val ws = wb.firstSheet
                ws.openStream().use { rows ->
                    rows.forEach { row ->
                        val r = row.rowNum - 1
                        for (c in 0 until row.cellCount) {
                            val cell = row.getCell(c) ?: continue
                            val raw = cell.text ?: ""
                            if (raw.isNotEmpty() && r in 0 until CellRef.MAX_ROWS && c < CellRef.MAX_COLS) {
                                sheet.set(r, c, raw)
                            }
                        }
                    }
                }
            }
        }
        return sheet
    }

    private fun loadCsv(file: File): Sheet {
        val sheet = Sheet(file.nameWithoutExtension)
        val sep = if (file.extension.equals("tsv", true)) '\t' else ','
        file.readLines().forEachIndexed { r, line ->
            if (r >= CellRef.MAX_ROWS) return@forEachIndexed
            splitCsv(line, sep).forEachIndexed { c, v ->
                if (c < CellRef.MAX_COLS && v.isNotEmpty()) sheet.set(r, c, v)
            }
        }
        return sheet
    }

    private fun splitCsv(line: String, sep: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> if (q && i + 1 < line.length && line[i+1] == '"') { sb.append('"'); i++ } else q = !q
                ch == sep && !q -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** Writes computed values (formulas evaluated to results). */
    fun saveXlsx(sheet: Sheet, file: File) {
        var maxR = 0; var maxC = 0
        for (key in sheet.cells.keys) {
            val r = (key / CellRef.MAX_COLS).toInt(); val c = (key % CellRef.MAX_COLS).toInt()
            if (r > maxR) maxR = r; if (c > maxC) maxC = c
        }
        FileOutputStream(file).use { fos ->
            val wb = Workbook(fos, "BRAHM Office", "1.1")
            val ws = wb.newWorksheet(sheet.name.ifBlank { "Sheet1" })
            for (r in 0..maxR) for (c in 0..maxC) {
                val raw = sheet.rawInput(r, c)
                if (raw.isEmpty()) continue
                val v = sheet.value(r, c)
                when (v) {
                    is Value.Num -> ws.value(r, c, v.n)
                    is Value.Bool -> ws.value(r, c, v.b)
                    else -> ws.value(r, c, v.asString())
                }
            }
            wb.finish()
        }
    }

    fun saveCsv(sheet: Sheet, file: File) {
        var maxR = 0; var maxC = 0
        for (key in sheet.cells.keys) {
            val r = (key / CellRef.MAX_COLS).toInt(); val c = (key % CellRef.MAX_COLS).toInt()
            if (r > maxR) maxR = r; if (c > maxC) maxC = c
        }
        file.bufferedWriter().use { w ->
            for (r in 0..maxR) {
                val sb = StringBuilder()
                for (c in 0..maxC) {
                    if (c > 0) sb.append(',')
                    val s = sheet.display(r, c)
                    if (s.contains(',') || s.contains('"')) sb.append('"').append(s.replace("\"", "\"\"")).append('"')
                    else sb.append(s)
                }
                w.write(sb.toString()); w.newLine()
            }
        }
    }
}
