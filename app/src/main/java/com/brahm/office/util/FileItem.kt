package com.brahm.office.util

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val type: DocType,
    val folder: String = ""
)
