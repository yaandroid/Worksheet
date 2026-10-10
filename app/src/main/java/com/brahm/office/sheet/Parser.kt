package com.brahm.office.sheet

/** Recursive-descent parser for spreadsheet formulas (without the leading '='). */
class Parser(private val src: String) {
    private var pos = 0

    fun parse(): Node {
        val n = parseExpr()
        skipWs()
        return n
    }

    // expr := compare
    private fun parseExpr(): Node = parseCompare()

    private fun parseCompare(): Node {
        var left = parseConcat()
        skipWs()
        while (true) {
            val op = matchAny("<=", ">=", "<>", "=", "<", ">") ?: break
            val right = parseConcat()
            left = Binary(op, left, right)
            skipWs()
        }
        return left
    }

    private fun parseConcat(): Node {
        var left = parseAdd()
        skipWs()
        while (peek() == '&') { pos++; left = Binary("&", left, parseAdd()); skipWs() }
        return left
    }

    private fun parseAdd(): Node {
        var left = parseMul()
        skipWs()
        while (true) {
            val c = peek()
            if (c == '+' || c == '-') { pos++; left = Binary(c.toString(), left, parseMul()); skipWs() }
            else break
        }
        return left
    }

    private fun parseMul(): Node {
        var left = parsePow()
        skipWs()
        while (true) {
            val c = peek()
            if (c == '*' || c == '/') { pos++; left = Binary(c.toString(), left, parsePow()); skipWs() }
            else break
        }
        return left
    }

    private fun parsePow(): Node {
        val left = parseUnary()
        skipWs()
        if (peek() == '^') { pos++; return Binary("^", left, parsePow()) }
        return left
    }

    private fun parseUnary(): Node {
        skipWs()
        val c = peek()
        if (c == '-' || c == '+') { pos++; return Unary(c, parseUnary()) }
        return parsePostfix()
    }

    private fun parsePostfix(): Node {
        var n = parsePrimary()
        skipWs()
        if (peek() == '%') { pos++; n = Percent(n) }
        return n
    }

    private fun parsePrimary(): Node {
        skipWs()
        val c = peek()
        when {
            c == '(' -> { pos++; val e = parseExpr(); skipWs(); expect(')'); return e }
            c == '"' -> return parseString()
            c.isDigit() || c == '.' -> return parseNumber()
            c.isLetter() -> return parseNameOrRef()
            else -> throw RuntimeException("Unexpected '$c'")
        }
    }

    private fun parseString(): Node {
        pos++ // opening quote
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos]
            if (ch == '"') {
                if (pos + 1 < src.length && src[pos + 1] == '"') { sb.append('"'); pos += 2; continue }
                pos++; return StrLit(sb.toString())
            }
            sb.append(ch); pos++
        }
        return StrLit(sb.toString())
    }

    private fun parseNumber(): Node {
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' ||
                   src[pos] == 'e' || src[pos] == 'E' ||
                   ((src[pos] == '+' || src[pos] == '-') && pos > start && (src[pos-1]=='e'||src[pos-1]=='E')))) pos++
        return NumLit(src.substring(start, pos).toDouble())
    }

    private fun parseNameOrRef(): Node {
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '_' || src[pos] == '.' || src[pos] == '$')) pos++
        val ident = src.substring(start, pos)
        skipWs()
        // function call?
        if (peek() == '(') {
            pos++
            val args = ArrayList<Node>()
            skipWs()
            if (peek() != ')') {
                args.add(parseExpr()); skipWs()
                while (peek() == ',' || peek() == ';') { pos++; args.add(parseExpr()); skipWs() }
            }
            expect(')')
            return Call(ident.uppercase(), args)
        }
        // boolean?
        if (ident.equals("TRUE", true)) return BoolLit(true)
        if (ident.equals("FALSE", true)) return BoolLit(false)
        // cell ref, maybe a range A1:B2
        val base = CellRef.parse(ident) ?: throw RuntimeException("Bad name $ident")
        if (peek() == ':') {
            pos++
            val s2 = pos
            while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] == '$')) pos++
            val end = CellRef.parse(src.substring(s2, pos)) ?: throw RuntimeException("Bad range")
            return RangeRef(
                minOf(base.first, end.first), minOf(base.second, end.second),
                maxOf(base.first, end.first), maxOf(base.second, end.second)
            )
        }
        return Ref(base.first, base.second)
    }

    private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'
    private fun skipWs() { while (pos < src.length && src[pos] == ' ') pos++ }
    private fun expect(c: Char) { if (peek() == c) pos++ else throw RuntimeException("Expected $c") }
    private fun matchAny(vararg ops: String): String? {
        for (op in ops) if (src.startsWith(op, pos)) { pos += op.length; return op }
        return null
    }
}
