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
import java.nio.ByteBuffer

/** On-device background replacement via ML Kit Selfie Segmentation.
 *  Extract frames -> segment each -> composite over a themed colour -> re-encode.
 *  Heavy; use on shorter clips. Fails gracefully (returns input on error). */
object BgRemover {

    private fun colorInt(hex: String): Triple<Int, Int, Int> {
        val c = hex.removePrefix("#")
        return Triple(c.substring(0, 2).toInt(16), c.substring(2, 4).toInt(16), c.substring(4, 6).toInt(16))
    }

    fun run(ctx: Context, input: String, bgHex: String, onStep: (String, Int) -> Unit): String {
        try {
            val work = File(ctx.cacheDir, "bg_${System.currentTimeMillis()}")
            val fin = File(work, "in"); val fout = File(work, "out")
            fin.mkdirs(); fout.mkdirs()
            onStep("background: extracting frames", 42)
            val ex = FFmpegKit.execute("-y -i \"$input\" -vf fps=30 \"${fin.absolutePath}/%05d.png\"")
            if (!ReturnCode.isSuccess(ex.returnCode)) return input

            val opts = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
            val seg = Segmentation.getClient(opts)
            val (br, bg, bb) = colorInt(bgHex)

            val frames = fin.listFiles { f -> f.name.endsWith(".png") }?.sortedBy { it.name } ?: emptyList()
            if (frames.isEmpty()) return input
            onStep("background: removing (${frames.size} frames)", 48)
            for ((i, fr) in frames.withIndex()) {
                val bmp = BitmapFactory.decodeFile(fr.absolutePath) ?: continue
                val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
                val mask = Tasks.await(seg.process(InputImage.fromBitmap(mutable, 0)))
                val buf: ByteBuffer = mask.buffer
                val mw = mask.width; val mh = mask.height
                val w = mutable.width; val h = mutable.height
                val px = IntArray(w * h); mutable.getPixels(px, 0, w, 0, 0, w, h)
                buf.rewind()
                // mask may differ in size; sample nearest
                for (y in 0 until h) {
                    val my = (y * mh / h).coerceIn(0, mh - 1)
                    for (x in 0 until w) {
                        val mx = (x * mw / w).coerceIn(0, mw - 1)
                        val conf = buf.getFloat((my * mw + mx) * 4)
                        if (conf < 0.5f) {
                            val idx = y * w + x
                            val a = px[idx] ushr 24
                            px[idx] = (a shl 24) or (br shl 16) or (bg shl 8) or bb
                        }
                    }
                }
                mutable.setPixels(px, 0, w, 0, 0, w, h)
                fr.outputStream().use { mutable.compress(Bitmap.CompressFormat.PNG, 90, it) }
                bmp.recycle()
                if (i % 15 == 0) onStep("background: ${i}/${frames.size}", 48 + (i * 12 / frames.size))
            }
            onStep("background: re-encoding", 62)
            val outFile = File(work, "bg.mp4").absolutePath
            val enc = FFmpegKit.execute(
                "-y -framerate 30 -i \"${fin.absolutePath}/%05d.png\" -i \"$input\" " +
                    "-map 0:v -map 1:a? -c:v libx264 -crf 20 -pix_fmt yuv420p -c:a aac -shortest \"$outFile\"")
            return if (ReturnCode.isSuccess(enc.returnCode)) outFile else input
        } catch (e: Exception) {
            return input
        }
    }
}
