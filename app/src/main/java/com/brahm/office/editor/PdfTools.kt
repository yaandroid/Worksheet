package com.brahm.office.editor

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Pure-Java PDF editing operations (no NDK) via PdfBox-Android. */
object PdfTools {

    fun pageCount(file: File): Int =
        PDDocument.load(file).use { it.numberOfPages }

    fun extractText(file: File): String =
        PDDocument.load(file).use { doc ->
            if (doc.isEncrypted) doc.setAllSecurityToBeRemoved(true)
            PDFTextStripper().getText(doc)
        }

    fun decrypt(input: File, password: String, output: File) {
        PDDocument.load(input, password).use { doc ->
            doc.setAllSecurityToBeRemoved(true)
            doc.save(output)
        }
    }

    fun encrypt(input: File, ownerPwd: String, userPwd: String, output: File) {
        PDDocument.load(input).use { doc ->
            val ap = AccessPermission()
            val policy = StandardProtectionPolicy(ownerPwd, userPwd, ap)
            policy.encryptionKeyLength = 128
            policy.permissions = ap
            doc.protect(policy)
            doc.save(output)
        }
    }

    /** Merge several PDFs into one. */
    fun merge(inputs: List<File>, output: File) {
        val merger = PDFMergerUtility()
        merger.destinationFileName = output.absolutePath
        inputs.forEach { merger.addSource(it) }
        merger.mergeDocuments(null)
    }

    /** Split into single-page files; returns the created files. */
    fun splitEachPage(input: File, outDir: File): List<File> {
        val results = ArrayList<File>()
        PDDocument.load(input).use { doc ->
            val parts = Splitter().split(doc)
            parts.forEachIndexed { i, part ->
                part.use {
                    val f = File(outDir, "${input.nameWithoutExtension}_p${i + 1}.pdf")
                    it.save(f); results.add(f)
                }
            }
        }
        return results
    }

    /** Extract a 1-based inclusive page range into a new file. */
    fun extractRange(input: File, from: Int, to: Int, output: File) {
        PDDocument.load(input).use { src ->
            PDDocument().use { dst ->
                val a = (from - 1).coerceAtLeast(0)
                val b = (to - 1).coerceAtMost(src.numberOfPages - 1)
                for (i in a..b) dst.addPage(src.getPage(i))
                dst.save(output)
            }
        }
    }

    fun rotatePage(input: File, pageIndex: Int, degrees: Int, output: File) {
        PDDocument.load(input).use { doc ->
            if (pageIndex in 0 until doc.numberOfPages) {
                val p = doc.getPage(pageIndex)
                p.rotation = (p.rotation + degrees) % 360
            }
            doc.save(output)
        }
    }

    fun rotateAll(input: File, degrees: Int, output: File) {
        PDDocument.load(input).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val p = doc.getPage(i)
                p.rotation = (p.rotation + degrees) % 360
            }
            doc.save(output)
        }
    }

    fun deletePage(input: File, pageIndex: Int, output: File) {
        PDDocument.load(input).use { doc ->
            if (pageIndex in 0 until doc.numberOfPages) doc.removePage(pageIndex)
            doc.save(output)
        }
    }

    fun share(context: Context, file: File) = shareAny(context, file, "application/pdf")

    fun shareAny(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share via"))
    }
}
