package com.brahm.autoeditor

import android.content.Context
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import java.io.File

/** On-device auto-edit — captions, grade, zoom, branding, audio, and optional
 *  keyword b-roll image overlays. Same FFmpeg graphs as the web engine. */
class AutoEditEngine(private val ctx: Context) {

    private val W = 1080; private val H = 1920; private val FPS = 30

    data class Options(
        val theme: String = "clinical_clean",
        val lang: String = "en",
        val denoise: String = "medium",
        val voiceClear: Boolean = true,
        val pitch: Double = 0.0,
        val brand: String = "BRAHM",
        val tag: String = "HOMEOPATHY",
        val aiMark: Boolean = true
    )

    interface Listener {
        fun onProgress(pct: Int, step: String)
        fun onDone(outputPath: String)
        fun onError(message: String)
    }

    private fun fontsDir(): File {
        val dir = File(ctx.cacheDir, "fonts"); if (!dir.exists()) dir.mkdirs()
        val names = ctx.assets.list("fonts") ?: emptyArray()
        for (n in names) {
            val f = File(dir, n)
            if (!f.exists()) ctx.assets.open("fonts/$n").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        return dir
    }

    private fun durationSec(path: String): Double = try {
        FFprobeKit.getMediaInformation(path).mediaInformation?.duration?.toDoubleOrNull() ?: 0.0
    } catch (e: Exception) { 0.0 }

    private fun zoom(t: Theme) =
        "zoompan=z='1.04+${t.zoomAmp}*sin(on/${t.zoomSpeed})':d=1:" +
            "x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=${W}x${H}:fps=$FPS"

    private fun branding(t: Theme, brand: String, tag: String, fonts: File, aiMark: Boolean): String {
        val col = t.brandColor.removePrefix("#")
        val pop = File(fonts, "Poppins-Bold.ttf").absolutePath
        val reg = File(fonts, "Poppins-Medium.ttf").absolutePath
        val b = brand.replace(":", "\\:").replace("'", "")
        val tg = tag.replace(":", "\\:").replace("'", "")
        var s = "drawtext=fontfile='$pop':text='$b':fontcolor=0x$col:fontsize=46:" +
            "x=w-tw-46:y=54:shadowcolor=black@0.45:shadowx=2:shadowy=2," +
            "drawtext=fontfile='$reg':text='$tg':fontcolor=0x$col@0.92:fontsize=20:" +
            "x=w-tw-46:y=108:shadowcolor=black@0.4:shadowx=1:shadowy=1"
        if (aiMark) s += ",drawtext=fontfile='$reg':text='AI-assisted edit':fontcolor=white@0.5:" +
            "fontsize=16:x=46:y=h-34:shadowcolor=black@0.4:shadowx=1:shadowy=1"
        return s
    }

    fun render(input: String, words: List<Word>, opt: Options,
               broll: List<Broll.Placed>, listener: Listener) {
        try {
            val t = Themes.byId(opt.theme)
            val fonts = fontsDir()
            val ass = File(ctx.cacheDir, "caps.ass")
            ass.writeText(Captions.buildAss(words, t, opt.lang))
            val out = File(ctx.getExternalFilesDir(null), "brahm_edit_${System.currentTimeMillis()}.mp4")
            val total = durationSec(input)

            // base video graph up to zoom
            val baseVf = "scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H," +
                "${t.grade},${zoom(t)}"

            // b-roll overlays (rounded circular cards that pop on keywords)
            val inputs = ArrayList<String>()
            inputs.add(input)
            val fc = StringBuilder()
            var cur = "bv"
            fc.append("[0:v]$baseVf[$cur];")
            val box = 640; val bx = (W - box) / 2; val by = 250
            for ((n, p) in broll.withIndex()) {
                inputs.add(p.imagePath)
                val idx = n + 1
                val lab = "im$n"; val nxt = "o$n"
                fc.append(
                    "[$idx:v]scale=$box:$box:force_original_aspect_ratio=increase,crop=$box:$box," +
                        "format=rgba,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':" +
                        "a='if(gt(hypot(X-$box/2,Y-$box/2),$box/2),0,255)'[$lab];")
                fc.append("[$cur][$lab]overlay=x=$bx:y=$by:enable='between(t,${p.start},${p.end})'[$nxt];")
                cur = nxt
            }
            // captions + branding on top
            fc.append("[$cur]subtitles='${ass.absolutePath}':fontsdir='${fonts.absolutePath}'," +
                "${branding(t, opt.brand, opt.tag, fonts, opt.aiMark)},format=yuv420p[v]")

            val af = AudioChain.build(opt.denoise, opt.voiceClear, opt.pitch)
            fc.append(";[0:a]$af[a]")

            val args = ArrayList<String>()
            args.add("-y")
            for (inp in inputs) { args.add("-i"); args.add(inp) }
            args.addAll(listOf("-filter_complex", fc.toString(), "-map", "[v]", "-map", "[a]",
                "-r", FPS.toString(), "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart",
                out.absolutePath))

            listener.onProgress(70, "editing (captions, b-roll, grade, audio)")
            FFmpegKit.executeWithArgumentsAsync(args.toTypedArray(), { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    listener.onProgress(100, "ready"); listener.onDone(out.absolutePath)
                } else listener.onError("FFmpeg failed: ${session.failStackTrace ?: session.returnCode}")
            }, { }, { stat ->
                if (total > 0) {
                    val pct = (70 + 30.0 * (stat.time / 1000.0) / total).toInt().coerceIn(70, 99)
                    listener.onProgress(pct, "editing…")
                }
            })
        } catch (e: Exception) {
            listener.onError(e.message ?: "engine error")
        }
    }
}
