package com.brahm.office.sheet

/** One worksheet with sparse storage and lazy, cached, cycle-safe evaluation. */
class Sheet(var name: String = "Sheet1") {

    class Cell(var input: String) {
        val isFormula get() = input.startsWith("=")
        var ast: Node? = null       // parsed lazily for formulas
        var parsed = false
    }

    val cells = HashMap<Long, Cell>()
    private val cache = HashMap<Long, Value>()
    private val visiting = HashSet<Long>()

    fun rawInput(r: Int, c: Int): String = cells[CellRef.key(r, c)]?.input ?: ""

    fun set(r: Int, c: Int, input: String) {
        val key = CellRef.key(r, c)
        if (input.isEmpty()) cells.remove(key)
        else cells[key] = Cell(input)
        cache.clear()          // simple + safe invalidation
    }

    fun value(r: Int, c: Int): Value = eval(CellRef.key(r, c))

    private fun eval(key: Long): Value {
        cache[key]?.let { return it }
        val cell = cells[key] ?: return Value.Blank
        if (!cell.isFormula) {
            val v = literal(cell.input)
            cache[key] = v
            return v
        }
        if (key in visiting) return Value.REF   // circular
        visiting.add(key)
        val result = try {
            if (!cell.parsed) {
                cell.ast = try { Parser(cell.input.substring(1)).parse() }
                           catch (e: Exception) { null }
                cell.parsed = true
            }
            val ast = cell.ast ?: Value.VALUE
            if (ast is Value) ast else Evaluator(this).eval(ast as Node)
        } catch (e: Exception) {
            Value.VALUE
        } finally {
            visiting.remove(key)
        }
        cache[key] = result
        return result
    }

    private fun literal(s: String): Value {
        val d = s.toDoubleOrNull()
        if (d != null) return Value.Num(d)
        if (s.equals("TRUE", true)) return Value.Bool(true)
        if (s.equals("FALSE", true)) return Value.Bool(false)
        return Value.Str(s)
    }

    fun display(r: Int, c: Int): String {
        val cell = cells[CellRef.key(r, c)] ?: return ""
        if (!cell.isFormula) return cell.input
        return value(r, c).asString()
    }

    fun clearCache() = cache.clear()
}
