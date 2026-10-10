package com.brahm.office.sheet

/** A computed cell value: number, text, boolean, or error. */
sealed class Value {
    data class Num(val n: Double) : Value()
    data class Str(val s: String) : Value()
    data class Bool(val b: Boolean) : Value()
    data class Err(val code: String) : Value()   // #DIV/0!, #VALUE!, #REF!, #NAME?, #N/A, #NUM!
    object Blank : Value()

    fun asNumber(): Double = when (this) {
        is Num -> n
        is Bool -> if (b) 1.0 else 0.0
        is Str -> s.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    fun asString(): String = when (this) {
        is Num -> if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
        is Str -> s
        is Bool -> if (b) "TRUE" else "FALSE"
        is Err -> code
        Blank -> ""
    }

    fun isError() = this is Err

    companion object {
        val DIV0 = Err("#DIV/0!")
        val VALUE = Err("#VALUE!")
        val REF = Err("#REF!")
        val NAME = Err("#NAME?")
        val NA = Err("#N/A")
        val NUM = Err("#NUM!")
    }
}
