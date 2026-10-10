package com.brahm.office.sheet

class Evaluator(val sheet: Sheet) {

    fun eval(node: Node): Value = when (node) {
        is NumLit -> Value.Num(node.v)
        is StrLit -> Value.Str(node.v)
        is BoolLit -> Value.Bool(node.v)
        is Ref -> sheet.value(node.row, node.col)
        is RangeRef -> flattenRange(node).firstOrNull() ?: Value.Blank
        is Percent -> Value.Num(eval(node.e).asNumber() / 100.0)
        is Unary -> {
            val v = eval(node.e)
            if (v.isError()) v else Value.Num(if (node.op == '-') -v.asNumber() else v.asNumber())
        }
        is Binary -> binary(node)
        is Call -> Functions.call(node.name, node.args, this)
        else -> Value.VALUE
    }

    private fun binary(n: Binary): Value {
        val l = eval(n.l); val r = eval(n.r)
        if (l.isError()) return l
        if (r.isError()) return r
        return when (n.op) {
            "&" -> Value.Str(l.asString() + r.asString())
            "+" -> Value.Num(l.asNumber() + r.asNumber())
            "-" -> Value.Num(l.asNumber() - r.asNumber())
            "*" -> Value.Num(l.asNumber() * r.asNumber())
            "/" -> if (r.asNumber() == 0.0) Value.DIV0 else Value.Num(l.asNumber() / r.asNumber())
            "^" -> Value.Num(Math.pow(l.asNumber(), r.asNumber()))
            "=" -> Value.Bool(cmp(l, r) == 0)
            "<>" -> Value.Bool(cmp(l, r) != 0)
            "<" -> Value.Bool(cmp(l, r) < 0)
            ">" -> Value.Bool(cmp(l, r) > 0)
            "<=" -> Value.Bool(cmp(l, r) <= 0)
            ">=" -> Value.Bool(cmp(l, r) >= 0)
            else -> Value.VALUE
        }
    }

    private fun cmp(a: Value, b: Value): Int {
        if (a is Value.Num && b is Value.Num) return a.n.compareTo(b.n)
        if (a is Value.Str && b is Value.Str) return a.s.compareTo(b.s, ignoreCase = true)
        if (a is Value.Num && b is Value.Str) return -1
        if (a is Value.Str && b is Value.Num) return 1
        return a.asString().compareTo(b.asString(), ignoreCase = true)
    }

    /** Expand a node to a flat list of values (ranges expanded, scalars singular). */
    fun flatten(node: Node): List<Value> = when (node) {
        is RangeRef -> flattenRange(node)
        else -> listOf(eval(node))
    }

    private fun flattenRange(n: RangeRef): List<Value> {
        val out = ArrayList<Value>()
        for (r in n.r1..n.r2) for (c in n.c1..n.c2) out.add(sheet.value(r, c))
        return out
    }

    /** Only numeric values from args (ranges expanded; text/blank ignored). */
    fun numbers(args: List<Node>): List<Double> {
        val out = ArrayList<Double>()
        for (a in args) for (v in flatten(a)) {
            when (v) {
                is Value.Num -> out.add(v.n)
                is Value.Bool -> out.add(if (v.b) 1.0 else 0.0)
                else -> {}
            }
        }
        return out
    }
}
