package com.brahm.autoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File

/** Fast on-device background replacement (ML Kit Selfie Segmentation).
 *  Processes at HALF resolution + JPEG frames + single-pass composite for speed.
 *  The main engine upscales the result back to 1080x1920. */
object BgRemover {
    private const val PW = 540
    private const val PH = 960
    private const val FPS = 24

    private fun colorInt(hex: String): Int {
        val c = hex.removePrefix("#")
        val r = c.substring(0, 2).toInt(16); val g = c.substring(2, 4).toInt(16); val b = c.substring(4, 6).toInt(16)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun run(ctx: Context, input: String, bgHex: String, onStep: (String, Int) -> Unit): String {
        try {
            val work = File(ctx.cacheDir, "bg_${System.currentTimeMillis()}")
            val fin = File(work, "in"); fin.mkdirs()
            onStep("background: extracting", 42)
            val ex = FFmpegKit.execute(
                "-y -i \"$input\" -vf \"fps=$FPS,scale=$PW:$PH:force_original_aspect_ratio=increase,crop=$PW:$PH\" " +
                    "-q:v 3 \"${fin.absolutePath}/%05d.jpg\"")
            if (!ReturnCode.isSuccess(ex.returnCode)) return input

            val seg = Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build())
            val bgColor = colorInt(bgHex)

            val frames = fin.listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
            if (frames.isEmpty()) return input
            onStep("background: removing (${frames.size} frames)", 46)
            val n = frames.size
            for ((i, fr) in frames.withIndex()) {
                val bmp = BitmapFactory.decodeFile(fr.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true) ?: continue
                val mask = Tasks.await(seg.process(InputImage.fromBitmap(bmp, 0)))
                val buf = mask.buffer; buf.rewind()
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
                // mask is the same size as the input bitmap in selfie segmentation
                val total = w * h
                var idx = 0
                while (idx < total) {
                    if (buf.float < 0.5f) px[idx] = bgColor
                    idx++
                }
                bmp.setPixels(px, 0, w, 0, 0, w, h)
                fr.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
                if (i % 10 == 0) onStep("background: $i/$n", 46 + (i * 18 / n))
            }
            onStep("background: re-encoding", 66)
            val outFile = File(work, "bg.mp4").absolutePath
            val enc = FFmpegKit.execute(
                "-y -framerate $FPS -i \"${fin.absolutePath}/%05d.jpg\" -i \"$input\" " +
                    "-map 0:v -map 1:a? -c:v libx264 -crf 22 -pix_fmt yuv420p -c:a aac -shortest \"$outFile\"")
            return if (ReturnCode.isSuccess(enc.returnCode)) outFile else input
        } catch (e: Exception) {
            return input
        }
    }
}
