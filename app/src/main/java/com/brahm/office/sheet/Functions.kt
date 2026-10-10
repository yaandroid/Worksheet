package com.brahm.office.sheet

import java.util.Calendar
import kotlin.math.*

/** Spreadsheet function library. Extend by adding entries to [table]. */
object Functions {

    private val rnd = java.util.Random()

    fun call(name: String, args: List<Node>, ev: Evaluator): Value {
        val fn = table[name] ?: return Value.NAME
        return try { fn(args, ev) } catch (e: Exception) { Value.VALUE }
    }

    fun names(): Set<String> = table.keys

    private fun num(n: Double) = Value.Num(n)
    private fun bool(b: Boolean) = Value.Bool(b)

    // criteria for COUNTIF/SUMIF: ">5", "<=3", "<>x", "apple", 5
    private fun matches(v: Value, crit: Value): Boolean {
        val c = crit.asString().trim()
        val ops = listOf(">=", "<=", "<>", ">", "<", "=")
        for (op in ops) if (c.startsWith(op)) {
            val rhs = c.substring(op.length).trim()
            val rn = rhs.toDoubleOrNull()
            return if (rn != null && v is Value.Num) {
                when (op) { ">=" -> v.n >= rn; "<=" -> v.n <= rn; ">" -> v.n > rn
                    "<" -> v.n < rn; "<>" -> v.n != rn; else -> v.n == rn }
            } else {
                val cmp = v.asString().compareTo(rhs, true)
                when (op) { "<>" -> v.asString() != rhs; "=" -> v.asString().equals(rhs, true)
                    ">" -> cmp > 0; "<" -> cmp < 0; ">=" -> cmp >= 0; "<=" -> cmp <= 0; else -> false }
            }
        }
        val cn = c.toDoubleOrNull()
        return if (cn != null && v is Value.Num) v.n == cn
        else v.asString().equals(c, true)
    }

    private fun serialToday(): Double {
        val epoch = Calendar.getInstance().apply { set(1899, 11, 30, 0, 0, 0) }
        val now = Calendar.getInstance()
        val days = (now.timeInMillis - epoch.timeInMillis) / 86400000.0
        return floor(days)
    }

    private val table: Map<String, (List<Node>, Evaluator) -> Value> = buildMap {

        // ---- Math ----
        put("SUM") { a, ev -> num(ev.numbers(a).sum()) }
        put("PRODUCT") { a, ev -> num(ev.numbers(a).fold(1.0) { p, x -> p * x }) }
        put("ABS") { a, ev -> num(abs(ev.eval(a[0]).asNumber())) }
        put("SIGN") { a, ev -> num(sign(ev.eval(a[0]).asNumber())) }
        put("SQRT") { a, ev -> val x = ev.eval(a[0]).asNumber(); if (x < 0) Value.NUM else num(sqrt(x)) }
        put("POWER") { a, ev -> num(Math.pow(ev.eval(a[0]).asNumber(), ev.eval(a[1]).asNumber())) }
        put("EXP") { a, ev -> num(exp(ev.eval(a[0]).asNumber())) }
        put("LN") { a, ev -> num(ln(ev.eval(a[0]).asNumber())) }
        put("LOG10") { a, ev -> num(log10(ev.eval(a[0]).asNumber())) }
        put("LOG") { a, ev -> val x = ev.eval(a[0]).asNumber(); val b = if (a.size > 1) ev.eval(a[1]).asNumber() else 10.0; num(ln(x) / ln(b)) }
        put("MOD") { a, ev -> val d = ev.eval(a[1]).asNumber(); if (d == 0.0) Value.DIV0 else { val n = ev.eval(a[0]).asNumber(); num(n - d * floor(n / d)) } }
        put("INT") { a, ev -> num(floor(ev.eval(a[0]).asNumber())) }
        put("TRUNC") { a, ev -> val x = ev.eval(a[0]).asNumber(); val d = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 0; val f = Math.pow(10.0, d.toDouble()); num(truncate(x*f)/f) }
        put("ROUND") { a, ev -> val x = ev.eval(a[0]).asNumber(); val d = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 0; val f = Math.pow(10.0, d.toDouble()); num(Math.round(x*f)/f) }
        put("ROUNDUP") { a, ev -> val x = ev.eval(a[0]).asNumber(); val d = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 0; val f = Math.pow(10.0, d.toDouble()); num(ceil(abs(x)*f)/f * sign(x)) }
        put("ROUNDDOWN") { a, ev -> val x = ev.eval(a[0]).asNumber(); val d = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 0; val f = Math.pow(10.0, d.toDouble()); num(floor(abs(x)*f)/f * sign(x)) }
        put("CEILING") { a, ev -> val x = ev.eval(a[0]).asNumber(); val s = if (a.size>1) ev.eval(a[1]).asNumber() else 1.0; if (s==0.0) num(0.0) else num(ceil(x/s)*s) }
        put("FLOOR") { a, ev -> val x = ev.eval(a[0]).asNumber(); val s = if (a.size>1) ev.eval(a[1]).asNumber() else 1.0; if (s==0.0) Value.DIV0 else num(floor(x/s)*s) }
        put("PI") { _, _ -> num(PI) }
        put("RAND") { _, _ -> num(rnd.nextDouble()) }
        put("RANDBETWEEN") { a, ev -> val lo = ev.eval(a[0]).asNumber().toInt(); val hi = ev.eval(a[1]).asNumber().toInt(); num((lo + rnd.nextInt(hi - lo + 1)).toDouble()) }
        put("GCD") { a, ev -> num(ev.numbers(a).map { it.toLong() }.reduce { x, y -> gcd(x, y) }.toDouble()) }
        put("SUMSQ") { a, ev -> num(ev.numbers(a).sumOf { it * it }) }
        put("SUMPRODUCT") { a, ev ->
            val lists = a.map { ev.flatten(it).map { v -> v.asNumber() } }
            val n = lists.minOf { it.size }
            var s = 0.0; for (i in 0 until n) { var p = 1.0; for (l in lists) p *= l[i]; s += p }; num(s)
        }

        // ---- Trig ----
        put("SIN") { a, ev -> num(sin(ev.eval(a[0]).asNumber())) }
        put("COS") { a, ev -> num(cos(ev.eval(a[0]).asNumber())) }
        put("TAN") { a, ev -> num(tan(ev.eval(a[0]).asNumber())) }
        put("ASIN") { a, ev -> num(asin(ev.eval(a[0]).asNumber())) }
        put("ACOS") { a, ev -> num(acos(ev.eval(a[0]).asNumber())) }
        put("ATAN") { a, ev -> num(atan(ev.eval(a[0]).asNumber())) }
        put("ATAN2") { a, ev -> num(atan2(ev.eval(a[1]).asNumber(), ev.eval(a[0]).asNumber())) }
        put("DEGREES") { a, ev -> num(Math.toDegrees(ev.eval(a[0]).asNumber())) }
        put("RADIANS") { a, ev -> num(Math.toRadians(ev.eval(a[0]).asNumber())) }

        // ---- Statistical ----
        put("AVERAGE") { a, ev -> val n = ev.numbers(a); if (n.isEmpty()) Value.DIV0 else num(n.average()) }
        put("COUNT") { a, ev -> num(ev.numbers(a).size.toDouble()) }
        put("COUNTA") { a, ev -> var c = 0; for (x in a) for (v in ev.flatten(x)) if (v != Value.Blank && v.asString().isNotEmpty()) c++; num(c.toDouble()) }
        put("COUNTBLANK") { a, ev -> var c = 0; for (x in a) for (v in ev.flatten(x)) if (v == Value.Blank || v.asString().isEmpty()) c++; num(c.toDouble()) }
        put("MAX") { a, ev -> val n = ev.numbers(a); num(if (n.isEmpty()) 0.0 else n.max()) }
        put("MIN") { a, ev -> val n = ev.numbers(a); num(if (n.isEmpty()) 0.0 else n.min()) }
        put("MEDIAN") { a, ev -> val n = ev.numbers(a).sorted(); if (n.isEmpty()) Value.NUM else { val m = n.size/2; num(if (n.size%2==1) n[m] else (n[m-1]+n[m])/2) } }
        put("MODE") { a, ev -> val n = ev.numbers(a); val g = n.groupingBy { it }.eachCount().maxByOrNull { it.value }; if (g==null) Value.NA else num(g.key) }
        put("STDEV") { a, ev -> stdev(ev.numbers(a), true) }
        put("STDEVP") { a, ev -> stdev(ev.numbers(a), false) }
        put("VAR") { a, ev -> variance(ev.numbers(a), true) }
        put("VARP") { a, ev -> variance(ev.numbers(a), false) }
        put("LARGE") { a, ev -> val n = ev.flatten(a[0]).filterIsInstance<Value.Num>().map{it.n}.sortedDescending(); val k = ev.eval(a[1]).asNumber().toInt(); if (k in 1..n.size) num(n[k-1]) else Value.NUM }
        put("SMALL") { a, ev -> val n = ev.flatten(a[0]).filterIsInstance<Value.Num>().map{it.n}.sorted(); val k = ev.eval(a[1]).asNumber().toInt(); if (k in 1..n.size) num(n[k-1]) else Value.NUM }
        put("RANK") { a, ev -> val x = ev.eval(a[0]).asNumber(); val list = ev.flatten(a[1]).filterIsInstance<Value.Num>().map{it.n}; val asc = a.size>2 && ev.eval(a[2]).asNumber()!=0.0; val sorted = if (asc) list.sorted() else list.sortedDescending(); val idx = sorted.indexOf(x); if (idx<0) Value.NA else num((idx+1).toDouble()) }
        put("COUNTIF") { a, ev -> val crit = ev.eval(a[1]); var c = 0; for (v in ev.flatten(a[0])) if (matches(v, crit)) c++; num(c.toDouble()) }
        put("SUMIF") { a, ev ->
            val rng = ev.flatten(a[0]); val crit = ev.eval(a[1])
            val sumR = if (a.size > 2) ev.flatten(a[2]) else rng
            var s = 0.0; for (i in rng.indices) if (matches(rng[i], crit) && i < sumR.size) s += sumR[i].asNumber(); num(s)
        }
        put("AVERAGEIF") { a, ev ->
            val rng = ev.flatten(a[0]); val crit = ev.eval(a[1])
            val avgR = if (a.size > 2) ev.flatten(a[2]) else rng
            var s = 0.0; var c = 0; for (i in rng.indices) if (matches(rng[i], crit) && i < avgR.size) { s += avgR[i].asNumber(); c++ }
            if (c == 0) Value.DIV0 else num(s / c)
        }

        // ---- Logical ----
        put("IF") { a, ev -> val cond = ev.eval(a[0]); if (cond.isError()) cond else if (truthy(cond)) ev.eval(a[1]) else if (a.size > 2) ev.eval(a[2]) else bool(false) }
        put("IFS") { a, ev -> var i = 0; while (i + 1 < a.size) { if (truthy(ev.eval(a[i]))) return@put ev.eval(a[i+1]); i += 2 }; Value.NA }
        put("AND") { a, ev -> var r = true; for (x in a) for (v in ev.flatten(x)) r = r && truthy(v); bool(r) }
        put("OR") { a, ev -> var r = false; for (x in a) for (v in ev.flatten(x)) r = r || truthy(v); bool(r) }
        put("NOT") { a, ev -> bool(!truthy(ev.eval(a[0]))) }
        put("XOR") { a, ev -> var c = 0; for (x in a) for (v in ev.flatten(x)) if (truthy(v)) c++; bool(c % 2 == 1) }
        put("TRUE") { _, _ -> bool(true) }
        put("FALSE") { _, _ -> bool(false) }
        put("IFERROR") { a, ev -> val v = ev.eval(a[0]); if (v.isError()) ev.eval(a[1]) else v }
        put("IFNA") { a, ev -> val v = ev.eval(a[0]); if (v == Value.NA) ev.eval(a[1]) else v }
        put("SWITCH") { a, ev ->
            val target = ev.eval(a[0]); var i = 1
            while (i + 1 < a.size) { if (ev.eval(a[i]).asString().equals(target.asString(), true)) return@put ev.eval(a[i+1]); i += 2 }
            if (i < a.size) ev.eval(a[i]) else Value.NA
        }

        // ---- Lookup ----
        put("VLOOKUP") { a, ev -> vlookup(a, ev) }
        put("HLOOKUP") { a, ev -> hlookup(a, ev) }
        put("INDEX") { a, ev -> index(a, ev) }
        put("MATCH") { a, ev -> match(a, ev) }
        put("CHOOSE") { a, ev -> val i = ev.eval(a[0]).asNumber().toInt(); if (i in 1..a.size-1) ev.eval(a[i]) else Value.VALUE }

        // ---- Text ----
        put("CONCAT") { a, ev -> val sb = StringBuilder(); for (x in a) for (v in ev.flatten(x)) sb.append(v.asString()); Value.Str(sb.toString()) }
        put("CONCATENATE") { a, ev -> val sb = StringBuilder(); for (x in a) sb.append(ev.eval(x).asString()); Value.Str(sb.toString()) }
        put("TEXTJOIN") { a, ev ->
            val delim = ev.eval(a[0]).asString(); val skip = truthy(ev.eval(a[1]))
            val parts = ArrayList<String>()
            for (i in 2 until a.size) for (v in ev.flatten(a[i])) { val s = v.asString(); if (!skip || s.isNotEmpty()) parts.add(s) }
            Value.Str(parts.joinToString(delim))
        }
        put("LEFT") { a, ev -> val s = ev.eval(a[0]).asString(); val n = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 1; Value.Str(s.take(n.coerceAtLeast(0))) }
        put("RIGHT") { a, ev -> val s = ev.eval(a[0]).asString(); val n = if (a.size>1) ev.eval(a[1]).asNumber().toInt() else 1; Value.Str(s.takeLast(n.coerceAtLeast(0))) }
        put("MID") { a, ev -> val s = ev.eval(a[0]).asString(); val start = ev.eval(a[1]).asNumber().toInt()-1; val len = ev.eval(a[2]).asNumber().toInt(); if (start<0||start>=s.length) Value.Str("") else Value.Str(s.substring(start, (start+len).coerceAtMost(s.length))) }
        put("LEN") { a, ev -> num(ev.eval(a[0]).asString().length.toDouble()) }
        put("TRIM") { a, ev -> Value.Str(ev.eval(a[0]).asString().trim().replace(Regex(" +"), " ")) }
        put("UPPER") { a, ev -> Value.Str(ev.eval(a[0]).asString().uppercase()) }
        put("LOWER") { a, ev -> Value.Str(ev.eval(a[0]).asString().lowercase()) }
        put("PROPER") { a, ev -> Value.Str(ev.eval(a[0]).asString().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }) }
        put("REPT") { a, ev -> Value.Str(ev.eval(a[0]).asString().repeat(ev.eval(a[1]).asNumber().toInt().coerceIn(0, 10000))) }
        put("SUBSTITUTE") { a, ev -> val s = ev.eval(a[0]).asString(); val old = ev.eval(a[1]).asString(); val new = ev.eval(a[2]).asString(); Value.Str(s.replace(old, new)) }
        put("REPLACE") { a, ev -> val s = ev.eval(a[0]).asString(); val start = ev.eval(a[1]).asNumber().toInt()-1; val len = ev.eval(a[2]).asNumber().toInt(); val new = ev.eval(a[3]).asString(); if (start<0) Value.VALUE else Value.Str(s.substring(0, start.coerceAtMost(s.length)) + new + s.substring((start+len).coerceIn(0, s.length))) }
        put("FIND") { a, ev -> val sub = ev.eval(a[0]).asString(); val s = ev.eval(a[1]).asString(); val i = s.indexOf(sub); if (i<0) Value.VALUE else num((i+1).toDouble()) }
        put("SEARCH") { a, ev -> val sub = ev.eval(a[0]).asString(); val s = ev.eval(a[1]).asString(); val i = s.indexOf(sub, ignoreCase = true); if (i<0) Value.VALUE else num((i+1).toDouble()) }
        put("VALUE") { a, ev -> val d = ev.eval(a[0]).asString().replace(",", "").toDoubleOrNull(); if (d==null) Value.VALUE else num(d) }
        put("TEXT") { a, ev -> val v = ev.eval(a[0]); Value.Str(v.asString()) }   // simplified
        put("CHAR") { a, ev -> Value.Str(ev.eval(a[0]).asNumber().toInt().toChar().toString()) }
        put("CODE") { a, ev -> val s = ev.eval(a[0]).asString(); if (s.isEmpty()) Value.VALUE else num(s[0].code.toDouble()) }
        put("EXACT") { a, ev -> bool(ev.eval(a[0]).asString() == ev.eval(a[1]).asString()) }

        // ---- Info ----
        put("ISBLANK") { a, ev -> bool(ev.eval(a[0]) == Value.Blank) }
        put("ISNUMBER") { a, ev -> bool(ev.eval(a[0]) is Value.Num) }
        put("ISTEXT") { a, ev -> bool(ev.eval(a[0]) is Value.Str) }
        put("ISLOGICAL") { a, ev -> bool(ev.eval(a[0]) is Value.Bool) }
        put("ISERROR") { a, ev -> bool(ev.eval(a[0]).isError()) }
        put("ISERR") { a, ev -> val v = ev.eval(a[0]); bool(v.isError() && v != Value.NA) }
        put("ISNA") { a, ev -> bool(ev.eval(a[0]) == Value.NA) }
        put("N") { a, ev -> num(ev.eval(a[0]).asNumber()) }
        put("NA") { _, _ -> Value.NA }

        // ---- Date/time ----
        put("TODAY") { _, _ -> num(serialToday()) }
        put("NOW") { _, _ -> num(serialToday()) }
        put("DATE") { a, ev -> val y = ev.eval(a[0]).asNumber().toInt(); val m = ev.eval(a[1]).asNumber().toInt(); val d = ev.eval(a[2]).asNumber().toInt(); val cal = Calendar.getInstance(); cal.set(1899,11,30,0,0,0); val base = cal.timeInMillis; cal.set(y, m-1, d, 0,0,0); num(floor((cal.timeInMillis - base)/86400000.0)) }
        put("YEAR") { a, ev -> dateField(ev.eval(a[0]).asNumber(), Calendar.YEAR) }
        put("MONTH") { a, ev -> dateField(ev.eval(a[0]).asNumber(), Calendar.MONTH, 1) }
        put("DAY") { a, ev -> dateField(ev.eval(a[0]).asNumber(), Calendar.DAY_OF_MONTH) }

        // ---- Financial ----
        put("PMT") { a, ev -> val r = ev.eval(a[0]).asNumber(); val n = ev.eval(a[1]).asNumber(); val pv = ev.eval(a[2]).asNumber(); val fv = if (a.size>3) ev.eval(a[3]).asNumber() else 0.0; if (r==0.0) num(-(pv+fv)/n) else num(-(pv*Math.pow(1+r,n)+fv)*r/(Math.pow(1+r,n)-1)) }
        put("FV") { a, ev -> val r = ev.eval(a[0]).asNumber(); val n = ev.eval(a[1]).asNumber(); val pmt = ev.eval(a[2]).asNumber(); val pv = if (a.size>3) ev.eval(a[3]).asNumber() else 0.0; if (r==0.0) num(-(pv+pmt*n)) else num(-(pv*Math.pow(1+r,n)+pmt*(Math.pow(1+r,n)-1)/r)) }
        put("PV") { a, ev -> val r = ev.eval(a[0]).asNumber(); val n = ev.eval(a[1]).asNumber(); val pmt = ev.eval(a[2]).asNumber(); val fv = if (a.size>3) ev.eval(a[3]).asNumber() else 0.0; if (r==0.0) num(-(fv+pmt*n)) else num(-(fv+pmt*(Math.pow(1+r,n)-1)/r)/Math.pow(1+r,n)) }
        put("NPV") { a, ev -> val r = ev.eval(a[0]).asNumber(); var s = 0.0; var t = 1; for (i in 1 until a.size) for (v in ev.flatten(a[i])) { s += v.asNumber()/Math.pow(1+r, t.toDouble()); t++ }; num(s) }
    }

    private fun truthy(v: Value): Boolean = when (v) {
        is Value.Bool -> v.b
        is Value.Num -> v.n != 0.0
        is Value.Str -> v.s.equals("TRUE", true)
        else -> false
    }

    private fun stdev(n: List<Double>, sample: Boolean): Value {
        if (n.size < (if (sample) 2 else 1)) return Value.DIV0
        val m = n.average(); val ss = n.sumOf { (it - m) * (it - m) }
        return num(sqrt(ss / (if (sample) n.size - 1 else n.size)))
    }
    private fun variance(n: List<Double>, sample: Boolean): Value {
        if (n.size < (if (sample) 2 else 1)) return Value.DIV0
        val m = n.average(); val ss = n.sumOf { (it - m) * (it - m) }
        return num(ss / (if (sample) n.size - 1 else n.size))
    }
    private fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)

    private fun dateField(serial: Double, field: Int, offset: Int = 0): Value {
        val cal = Calendar.getInstance(); cal.set(1899, 11, 30, 0, 0, 0)
        cal.add(Calendar.DAY_OF_MONTH, serial.toInt())
        return Value.Num((cal.get(field) + offset).toDouble())
    }

    private fun vlookup(a: List<Node>, ev: Evaluator): Value {
        val key = ev.eval(a[0]); val range = a[1]
        val colIdx = ev.eval(a[2]).asNumber().toInt()
        val approx = a.size < 4 || truthy(ev.eval(a[3]))
        if (range !is RangeRef) return Value.NA
        var best = -1
        for (r in range.r1..range.r2) {
            val v = ev.sheet.value(r, range.c1)
            if (exactEq(v, key)) { return cellInRange(ev, range, r, colIdx) }
            if (approx && v is Value.Num && key is Value.Num && v.n <= key.n) best = r
        }
        return if (approx && best >= 0) cellInRange(ev, range, best, colIdx) else Value.NA
    }
    private fun hlookup(a: List<Node>, ev: Evaluator): Value {
        val key = ev.eval(a[0]); val range = a[1]
        val rowIdx = ev.eval(a[2]).asNumber().toInt()
        if (range !is RangeRef) return Value.NA
        for (c in range.c1..range.c2) {
            val v = ev.sheet.value(range.r1, c)
            if (exactEq(v, key)) { val rr = range.r1 + rowIdx - 1; return if (rr in range.r1..range.r2) ev.sheet.value(rr, c) else Value.REF }
        }
        return Value.NA
    }
    private fun cellInRange(ev: Evaluator, range: RangeRef, row: Int, colIdx: Int): Value {
        val cc = range.c1 + colIdx - 1
        return if (cc in range.c1..range.c2) ev.sheet.value(row, cc) else Value.REF
    }
    private fun index(a: List<Node>, ev: Evaluator): Value {
        val range = a[0]
        if (range !is RangeRef) return Value.REF
        val rn = ev.eval(a[1]).asNumber().toInt()
        val cn = if (a.size > 2) ev.eval(a[2]).asNumber().toInt() else 1
        val rows = range.r2 - range.r1 + 1; val cols = range.c2 - range.c1 + 1
        // single row or column: allow 1-arg indexing
        return if (rows == 1) { val c = range.c1 + rn - 1; if (c in range.c1..range.c2) ev.sheet.value(range.r1, c) else Value.REF }
        else if (cols == 1) { val r = range.r1 + rn - 1; if (r in range.r1..range.r2) ev.sheet.value(r, range.c1) else Value.REF }
        else { val r = range.r1 + rn - 1; val c = range.c1 + cn - 1; if (r in range.r1..range.r2 && c in range.c1..range.c2) ev.sheet.value(r, c) else Value.REF }
    }
    private fun match(a: List<Node>, ev: Evaluator): Value {
        val key = ev.eval(a[0]); val range = a[1]
        val type = if (a.size > 2) ev.eval(a[2]).asNumber().toInt() else 1
        if (range !is RangeRef) return Value.NA
        val vals = ArrayList<Value>()
        for (r in range.r1..range.r2) for (c in range.c1..range.c2) vals.add(ev.sheet.value(r, c))
        when (type) {
            0 -> { vals.forEachIndexed { i, v -> if (exactEq(v, key)) return num((i + 1).toDouble()) }; return Value.NA }
            1 -> { var idx = -1; vals.forEachIndexed { i, v -> if (v is Value.Num && key is Value.Num && v.n <= key.n) idx = i }; return if (idx>=0) num((idx+1).toDouble()) else Value.NA }
            else -> { var idx = -1; vals.forEachIndexed { i, v -> if (v is Value.Num && key is Value.Num && v.n >= key.n) idx = i }; return if (idx>=0) num((idx+1).toDouble()) else Value.NA }
        }
    }
    private fun exactEq(a: Value, b: Value): Boolean =
        if (a is Value.Num && b is Value.Num) a.n == b.n
        else a.asString().equals(b.asString(), true)
}
