package com.brahm.office.util

import java.util.Locale

enum class DocType(val label: String) {
    PDF("PDF"), WORD("Word"), EXCEL("Excel"), CSV("CSV"),
    PPT("PowerPoint"), TEXT("Text"), OTHER("File");

    companion object {
        fun fromName(name: String): DocType {
            val n = name.lowercase(Locale.ROOT)
            return when {
                n.endsWith(".pdf") -> PDF
                n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".dotx") -> WORD
                n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".xlsm") -> EXCEL
                n.endsWith(".csv") || n.endsWith(".tsv") -> CSV
                n.endsWith(".ppt") || n.endsWith(".pptx") -> PPT
                n.endsWith(".txt") -> TEXT
                else -> OTHER
            }
        }

        fun fromMime(mime: String?): DocType = when (mime) {
            "application/pdf" -> PDF
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> WORD
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> EXCEL
            "text/csv" -> CSV
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PPT
            "text/plain" -> TEXT
            else -> OTHER
        }
    }
}
