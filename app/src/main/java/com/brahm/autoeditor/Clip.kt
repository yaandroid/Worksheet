package com.brahm.autoeditor

/** One timeline clip — a video segment or a still image.
 *  CapCut-style per-clip edits: trim, speed, crop-mode, volume, filter/adjust. */
data class Clip(
    var path: String,
    var isImage: Boolean = false,
    var srcIn: Double = 0.0,       // trim in-point in the source (video)
    var srcOut: Double = 0.0,      // trim out-point in the source (video)
    var srcDur: Double = 0.0,      // full source duration (video)
    var imageDur: Double = 3.0,    // on-screen duration for an image
    var speed: Double = 1.0,       // 0.25 .. 4.0
    var volume: Double = 1.0,      // 0 = muted ("Original sound" off)
    var fit: Boolean = false,      // false = fill (crop), true = fit (pad/letterbox)
    var adjust: String = "",       // extra per-clip eq/curves (Filter / Adjust)
    var label: String = "clip"
) {
    /** On-timeline duration after trim + speed. */
    fun outDur(): Double =
        if (isImage) (imageDur / speed).coerceAtLeast(0.1)
        else ((srcOut - srcIn) / speed).coerceAtLeast(0.1)
}
