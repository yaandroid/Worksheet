package com.brahm.autoeditor

import kotlin.math.abs
import kotlin.math.pow

/** Audio: noise removal, voice clarity, pitch. Mirror of audio.py.
 *  Pitch uses asetrate+atempo (no librubberband dependency) so it works on
 *  every ffmpeg-kit build. */
object AudioChain {
    fun build(denoise: String, voiceClear: Boolean, pitchSemitones: Double, loudnorm: Boolean = true): String {
        val ch = ArrayList<String>()
        ch.add("highpass=f=85")
        val nf = when (denoise) {
            "light" -> "-18"; "medium" -> "-25"; "strong" -> "-32"; else -> null
        }
        if (nf != null) ch.add("afftdn=nf=$nf:nt=w")
        if (voiceClear) {
            ch.add("deesser=i=0.4")
            ch.add("equalizer=f=220:t=q:w=1.2:g=-2")
            ch.add("equalizer=f=3200:t=q:w=1.4:g=3")
            ch.add("acompressor=threshold=-18dB:ratio=3:attack=12:release=180:makeup=2")
            ch.add("speechnorm=e=12.5:r=0.00025:l=1")
        }
        if (abs(pitchSemitones) > 0.01) {
            val factor = 2.0.pow(pitchSemitones / 12.0)
            // shift pitch, then restore tempo
            ch.add("asetrate=44100*%.5f".format(factor))
            ch.add("aresample=44100")
            ch.add("atempo=%.5f".format(1.0 / factor))
        }
        if (loudnorm) ch.add("loudnorm=I=-16:TP=-1.5:LRA=11")
        ch.add("alimiter=limit=0.97")
        return ch.joinToString(",")
    }
}
