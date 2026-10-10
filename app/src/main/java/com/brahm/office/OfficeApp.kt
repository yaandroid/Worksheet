package com.brahm.office

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** BRAHM Office — Brahm InnerBrain Technologies Pvt. Ltd. */
class OfficeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise PdfBox-Android font/resource loader once.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
