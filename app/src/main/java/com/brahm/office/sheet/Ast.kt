package com.brahm.office.sheet

/** Formula AST nodes. */
sealed class Node
class NumLit(val v: Double) : Node()
class StrLit(val v: String) : Node()
class BoolLit(val v: Boolean) : Node()
class Ref(val row: Int, val col: Int) : Node()
class RangeRef(val r1: Int, val c1: Int, val r2: Int, val c2: Int) : Node()
class Unary(val op: Char, val e: Node) : Node()
class Binary(val op: String, val l: Node, val r: Node) : Node()
class Percent(val e: Node) : Node()
class Call(val name: String, val args: List<Node>) : Node()
