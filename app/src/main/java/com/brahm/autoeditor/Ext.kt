package com.brahm.autoeditor

import android.widget.SeekBar

/** Small helper: fire a callback with the current progress on change. */
fun SeekBar.setOnSeekBarChangeListenerSimple(onChange: (Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    })
}
