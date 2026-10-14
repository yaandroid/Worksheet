// BRAHM Office — spreadsheet engine (vanilla JS)
// Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
(function (global) {
  const MAX_ROWS = 10000, MAX_COLS = 702; // A..ZZ

  function colName(c) { let s = ""; c++; while (c > 0) { const m = (c - 1) % 26; s = String.fromCharCode(65 + m) + s; c = Math.floor((c - 1) / 26); } return s; }
  function colIndex(letters) { let n = 0; for (const ch of letters.toUpperCase()) { if (ch < 'A' || ch > 'Z') break; n = n * 26 + (ch.charCodeAt(0) - 64); } return n - 1; }
  function parseRef(ref) { const m = /^\$?([A-Za-z]{1,2})\$?(\d{1,6})$/.exec(ref.trim()); if (!m) return null; const c = colIndex(m[1]), r = parseInt(m[2]) - 1; if (c < 0 || c >= MAX_COLS || r < 0 || r >= MAX_ROWS) return null; return [r, c]; }
  const key = (r, c) => r * MAX_COLS + c;

  // ---- Values ----
  const ERR = (code) => ({ t: 'err', code });
  const DIV0 = ERR('#DIV/0!'), VALUE = ERR('#VALUE!'), REF = ERR('#REF!'), NAME = ERR('#NAME?'), NA = ERR('#N/A'), NUM = ERR('#NUM!');
  const Num = (n) => ({ t: 'num', n }); const Str = (s) => ({ t: 'str', s }); const Bool = (b) => ({ t: 'bool', b }); const BLANK = { t: 'blank' };
  function asNum(v) { if (!v) return 0; if (v.t === 'num') return v.n; if (v.t === 'bool') return v.b ? 1 : 0; if (v.t === 'str') { const d = parseFloat(v.s); return isNaN(d) ? 0 : d; } return 0; }
  function asStr(v) { if (!v) return ""; switch (v.t) { case 'num': return numToStr(v.n); case 'str': return v.s; case 'bool': return v.b ? "TRUE" : "FALSE"; case 'err': return v.code; default: return ""; } }
  function isErr(v) { return v && v.t === 'err'; }
  function numToStr(n) { if (Number.isInteger(n)) return String(n); return String(Math.round(n * 1e10) / 1e10); }

  // ---- Tokenizer + Parser (recursive descent) ----
  function Parser(src) { this.s = src; this.i = 0; }
  Parser.prototype.parse = function () { const n = this.expr(); return n; };
  Parser.prototype.ws = function () { while (this.i < this.s.length && this.s[this.i] === ' ') this.i++; };
  Parser.prototype.peek = function () { return this.i < this.s.length ? this.s[this.i] : '\0'; };
  Parser.prototype.expr = function () { return this.compare(); };
  Parser.prototype.matchAny = function (ops) { for (const op of ops) if (this.s.startsWith(op, this.i)) { this.i += op.length; return op; } return null; };
  Parser.prototype.compare = function () { let l = this.concat(); this.ws(); let op; while ((op = this.matchAny(['<=', '>=', '<>', '=', '<', '>']))) { l = { k: 'bin', op, l, r: this.concat() }; this.ws(); } return l; };
  Parser.prototype.concat = function () { let l = this.add(); this.ws(); while (this.peek() === '&') { this.i++; l = { k: 'bin', op: '&', l, r: this.add() }; this.ws(); } return l; };
  Parser.prototype.add = function () { let l = this.mul(); this.ws(); let c; while ((c = this.peek()) === '+' || c === '-') { this.i++; l = { k: 'bin', op: c, l, r: this.mul() }; this.ws(); } return l; };
  Parser.prototype.mul = function () { let l = this.pow(); this.ws(); let c; while ((c = this.peek()) === '*' || c === '/') { this.i++; l = { k: 'bin', op: c, l, r: this.pow() }; this.ws(); } return l; };
  Parser.prototype.pow = function () { const l = this.unary(); this.ws(); if (this.peek() === '^') { this.i++; return { k: 'bin', op: '^', l, r: this.pow() }; } return l; };
  Parser.prototype.unary = function () { this.ws(); const c = this.peek(); if (c === '-' || c === '+') { this.i++; return { k: 'un', op: c, e: this.unary() }; } return this.postfix(); };
  Parser.prototype.postfix = function () { let n = this.primary(); this.ws(); if (this.peek() === '%') { this.i++; n = { k: 'pct', e: n }; } return n; };
  Parser.prototype.primary = function () { this.ws(); const c = this.peek();
    if (c === '(') { this.i++; const e = this.expr(); this.ws(); if (this.peek() === ')') this.i++; return e; }
    if (c === '"') return this.str();
    if ((c >= '0' && c <= '9') || c === '.') return this.number();
    if (/[A-Za-z]/.test(c)) return this.nameOrRef();
    throw new Error('unexpected ' + c);
  };
  Parser.prototype.str = function () { this.i++; let s = ''; while (this.i < this.s.length) { const ch = this.s[this.i]; if (ch === '"') { if (this.s[this.i + 1] === '"') { s += '"'; this.i += 2; continue; } this.i++; return { k: 'str', v: s }; } s += ch; this.i++; } return { k: 'str', v: s }; };
  Parser.prototype.number = function () { const start = this.i; while (this.i < this.s.length && /[0-9.eE]/.test(this.s[this.i]) || ((this.s[this.i] === '+' || this.s[this.i] === '-') && (this.s[this.i-1]==='e'||this.s[this.i-1]==='E'))) this.i++; return { k: 'num', v: parseFloat(this.s.slice(start, this.i)) }; };
  Parser.prototype.nameOrRef = function () { const start = this.i; while (this.i < this.s.length && /[A-Za-z0-9_.$]/.test(this.s[this.i])) this.i++; const id = this.s.slice(start, this.i); this.ws();
    if (this.peek() === '(') { this.i++; const args = []; this.ws(); if (this.peek() !== ')') { args.push(this.expr()); this.ws(); while (this.peek() === ',' || this.peek() === ';') { this.i++; args.push(this.expr()); this.ws(); } } if (this.peek() === ')') this.i++; return { k: 'call', name: id.toUpperCase(), args }; }
    if (/^true$/i.test(id)) return { k: 'bool', v: true }; if (/^false$/i.test(id)) return { k: 'bool', v: false };
    const base = parseRef(id); if (!base) throw new Error('bad name ' + id);
    if (this.peek() === ':') { this.i++; const s2 = this.i; while (this.i < this.s.length && /[A-Za-z0-9$]/.test(this.s[this.i])) this.i++; const end = parseRef(this.s.slice(s2, this.i)); if (!end) throw new Error('bad range'); return { k: 'range', r1: Math.min(base[0], end[0]), c1: Math.min(base[1], end[1]), r2: Math.max(base[0], end[0]), c2: Math.max(base[1], end[1]) }; }
    return { k: 'ref', r: base[0], c: base[1] };
  };

  // ---- Sheet ----
  function Sheet(name) { this.name = name || 'Sheet1'; this.cells = new Map(); this.cache = new Map(); this.visiting = new Set(); }
  Sheet.prototype.raw = function (r, c) { const cell = this.cells.get(key(r, c)); return cell ? cell.input : ''; };
  Sheet.prototype.set = function (r, c, input) { const k = key(r, c); if (input === '' || input == null) this.cells.delete(k); else this.cells.set(k, { input, ast: undefined }); this.cache.clear(); };
  Sheet.prototype.value = function (r, c) { return this.eval(key(r, c)); };
  Sheet.prototype.eval = function (k) {
    if (this.cache.has(k)) return this.cache.get(k);
    const cell = this.cells.get(k); if (!cell) return BLANK;
    if (cell.input[0] !== '=') { const v = literal(cell.input); this.cache.set(k, v); return v; }
    if (this.visiting.has(k)) return REF;
    this.visiting.add(k);
    let res;
    try { if (cell.ast === undefined) { try { cell.ast = new Parser(cell.input.slice(1)).parse(); } catch (e) { cell.ast = null; } } res = cell.ast ? new Eval(this, k).eval(cell.ast) : VALUE; }
    catch (e) { res = VALUE; } finally { this.visiting.delete(k); }
    this.cache.set(k, res); return res;
  };
  Sheet.prototype.display = function (r, c) { const cell = this.cells.get(key(r, c)); if (!cell) return ''; if (cell.input[0] !== '=') return cell.input; return asStr(this.value(r, c)); };
  function literal(s) { const d = Number(s); if (s.trim() !== '' && !isNaN(d)) return Num(d); if (/^true$/i.test(s)) return Bool(true); if (/^false$/i.test(s)) return Bool(false); return Str(s); }

  // ---- Evaluator ----
  function Eval(sheet, curKey) { this.sheet = sheet; if (curKey != null) { this.curR = Math.floor(curKey / MAX_COLS); this.curC = curKey % MAX_COLS; } }
  Eval.prototype.eval = function (n) {
    switch (n.k) {
      case 'num': return Num(n.v); case 'str': return Str(n.v); case 'bool': return Bool(n.v);
      case 'ref': return this.sheet.value(n.r, n.c);
      case 'range': { const f = this.flattenRange(n); return f.length ? f[0] : BLANK; }
      case 'pct': return Num(asNum(this.eval(n.e)) / 100);
      case 'un': { const v = this.eval(n.e); if (isErr(v)) return v; return Num(n.op === '-' ? -asNum(v) : asNum(v)); }
      case 'bin': return this.bin(n);
      case 'call': return Functions.call(n.name, n.args, this);
    } return VALUE;
  };
  Eval.prototype.bin = function (n) { const l = this.eval(n.l), r = this.eval(n.r); if (isErr(l)) return l; if (isErr(r)) return r;
    switch (n.op) { case '&': return Str(asStr(l) + asStr(r)); case '+': return Num(asNum(l) + asNum(r)); case '-': return Num(asNum(l) - asNum(r)); case '*': return Num(asNum(l) * asNum(r)); case '/': return asNum(r) === 0 ? DIV0 : Num(asNum(l) / asNum(r)); case '^': return Num(Math.pow(asNum(l), asNum(r)));
      case '=': return Bool(cmp(l, r) === 0); case '<>': return Bool(cmp(l, r) !== 0); case '<': return Bool(cmp(l, r) < 0); case '>': return Bool(cmp(l, r) > 0); case '<=': return Bool(cmp(l, r) <= 0); case '>=': return Bool(cmp(l, r) >= 0); }
    return VALUE;
  };
  function cmp(a, b) { if (a.t === 'num' && b.t === 'num') return a.n - b.n; if (a.t === 'str' && b.t === 'str') return a.s.toLowerCase() < b.s.toLowerCase() ? -1 : (a.s.toLowerCase() > b.s.toLowerCase() ? 1 : 0); return asStr(a).toLowerCase() < asStr(b).toLowerCase() ? -1 : (asStr(a).toLowerCase() > asStr(b).toLowerCase() ? 1 : 0); }
  Eval.prototype.flatten = function (n) { if (n.k === 'range') return this.flattenRange(n); return [this.eval(n)]; };
  Eval.prototype.flattenRange = function (n) { const out = []; for (let r = n.r1; r <= n.r2; r++) for (let c = n.c1; c <= n.c2; c++) out.push(this.sheet.value(r, c)); return out; };
  Eval.prototype.numbers = function (args) { const out = []; for (const a of args) for (const v of this.flatten(a)) { if (v.t === 'num') out.push(v.n); else if (v.t === 'bool') out.push(v.b ? 1 : 0); } return out; };

  // ---- Functions ----
  const Functions = { map: {}, call(name, args, ev) { const f = this.map[name]; if (!f) return NAME; try { return f(args, ev); } catch (e) { return VALUE; } }, names() { return Object.keys(this.map); } };
  const truthy = (v) => v.t === 'bool' ? v.b : (v.t === 'num' ? v.n !== 0 : (v.t === 'str' ? /^true$/i.test(v.s) : false));
  function matches(v, crit) { const c = asStr(crit).trim(); for (const op of ['>=', '<=', '<>', '>', '<', '=']) { if (c.startsWith(op)) { const rhs = c.slice(op.length).trim(); const rn = parseFloat(rhs); if (!isNaN(rn) && v.t === 'num') { switch (op) { case '>=': return v.n >= rn; case '<=': return v.n <= rn; case '>': return v.n > rn; case '<': return v.n < rn; case '<>': return v.n !== rn; default: return v.n === rn; } } else { const s = asStr(v); switch (op) { case '<>': return s !== rhs; case '=': return s.toLowerCase() === rhs.toLowerCase(); case '>': return s > rhs; case '<': return s < rhs; case '>=': return s >= rhs; case '<=': return s <= rhs; } } } } const cn = parseFloat(c); if (!isNaN(cn) && v.t === 'num') return v.n === cn; return asStr(v).toLowerCase() === c.toLowerCase(); }
  const F = (name, fn) => { Functions.map[name] = fn; };

  // Math
  F('SUM', (a, ev) => Num(ev.numbers(a).reduce((x, y) => x + y, 0)));
  F('PRODUCT', (a, ev) => Num(ev.numbers(a).reduce((x, y) => x * y, 1)));
  F('ABS', (a, ev) => Num(Math.abs(asNum(ev.eval(a[0])))));
  F('SQRT', (a, ev) => { const x = asNum(ev.eval(a[0])); return x < 0 ? NUM : Num(Math.sqrt(x)); });
  F('POWER', (a, ev) => Num(Math.pow(asNum(ev.eval(a[0])), asNum(ev.eval(a[1])))));
  F('EXP', (a, ev) => Num(Math.exp(asNum(ev.eval(a[0])))));
  F('LN', (a, ev) => Num(Math.log(asNum(ev.eval(a[0])))));
  F('LOG10', (a, ev) => Num(Math.log10(asNum(ev.eval(a[0])))));
  F('LOG', (a, ev) => { const x = asNum(ev.eval(a[0])); const b = a.length > 1 ? asNum(ev.eval(a[1])) : 10; return Num(Math.log(x) / Math.log(b)); });
  F('MOD', (a, ev) => { const d = asNum(ev.eval(a[1])); if (d === 0) return DIV0; const n = asNum(ev.eval(a[0])); return Num(n - d * Math.floor(n / d)); });
  F('INT', (a, ev) => Num(Math.floor(asNum(ev.eval(a[0])))));
  F('ROUND', (a, ev) => { const x = asNum(ev.eval(a[0])); const d = a.length > 1 ? asNum(ev.eval(a[1])) : 0; const f = Math.pow(10, d); return Num(Math.round(x * f) / f); });
  F('ROUNDUP', (a, ev) => { const x = asNum(ev.eval(a[0])); const d = a.length > 1 ? asNum(ev.eval(a[1])) : 0; const f = Math.pow(10, d); return Num(Math.ceil(Math.abs(x) * f) / f * Math.sign(x)); });
  F('ROUNDDOWN', (a, ev) => { const x = asNum(ev.eval(a[0])); const d = a.length > 1 ? asNum(ev.eval(a[1])) : 0; const f = Math.pow(10, d); return Num(Math.floor(Math.abs(x) * f) / f * Math.sign(x)); });
  F('CEILING', (a, ev) => { const x = asNum(ev.eval(a[0])); const s = a.length > 1 ? asNum(ev.eval(a[1])) : 1; return s === 0 ? Num(0) : Num(Math.ceil(x / s) * s); });
  F('FLOOR', (a, ev) => { const x = asNum(ev.eval(a[0])); const s = a.length > 1 ? asNum(ev.eval(a[1])) : 1; return s === 0 ? DIV0 : Num(Math.floor(x / s) * s); });
  F('PI', () => Num(Math.PI));
  F('SIGN', (a, ev) => Num(Math.sign(asNum(ev.eval(a[0])))));
  F('TRUNC', (a, ev) => { const x = asNum(ev.eval(a[0])); const d = a.length > 1 ? asNum(ev.eval(a[1])) : 0; const f = Math.pow(10, d); return Num(Math.trunc(x * f) / f); });
  F('RAND', () => Num(Math.random()));
  F('RANDBETWEEN', (a, ev) => { const lo = Math.ceil(asNum(ev.eval(a[0]))), hi = Math.floor(asNum(ev.eval(a[1]))); return Num(lo + Math.floor(Math.random() * (hi - lo + 1))); });
  F('SUMPRODUCT', (a, ev) => { const lists = a.map(x => ev.flatten(x).map(asNum)); const n = Math.min(...lists.map(l => l.length)); let s = 0; for (let i = 0; i < n; i++) { let p = 1; for (const l of lists) p *= l[i]; s += p; } return Num(s); });
  F('SUMSQ', (a, ev) => Num(ev.numbers(a).reduce((x, y) => x + y * y, 0)));
  // Trig
  F('SIN', (a, ev) => Num(Math.sin(asNum(ev.eval(a[0]))))); F('COS', (a, ev) => Num(Math.cos(asNum(ev.eval(a[0]))))); F('TAN', (a, ev) => Num(Math.tan(asNum(ev.eval(a[0])))));
  F('ASIN', (a, ev) => Num(Math.asin(asNum(ev.eval(a[0]))))); F('ACOS', (a, ev) => Num(Math.acos(asNum(ev.eval(a[0]))))); F('ATAN', (a, ev) => Num(Math.atan(asNum(ev.eval(a[0])))));
  F('ATAN2', (a, ev) => Num(Math.atan2(asNum(ev.eval(a[1])), asNum(ev.eval(a[0]))))); F('DEGREES', (a, ev) => Num(asNum(ev.eval(a[0])) * 180 / Math.PI)); F('RADIANS', (a, ev) => Num(asNum(ev.eval(a[0])) * Math.PI / 180));
  // Stats
  F('AVERAGE', (a, ev) => { const n = ev.numbers(a); return n.length ? Num(n.reduce((x, y) => x + y, 0) / n.length) : DIV0; });
  F('COUNT', (a, ev) => Num(ev.numbers(a).length));
  F('COUNTA', (a, ev) => { let c = 0; for (const x of a) for (const v of ev.flatten(x)) if (v.t !== 'blank' && asStr(v) !== '') c++; return Num(c); });
  F('COUNTBLANK', (a, ev) => { let c = 0; for (const x of a) for (const v of ev.flatten(x)) if (v.t === 'blank' || asStr(v) === '') c++; return Num(c); });
  F('MAX', (a, ev) => { const n = ev.numbers(a); return Num(n.length ? Math.max(...n) : 0); });
  F('MIN', (a, ev) => { const n = ev.numbers(a); return Num(n.length ? Math.min(...n) : 0); });
  F('MEDIAN', (a, ev) => { const n = ev.numbers(a).sort((x, y) => x - y); if (!n.length) return NUM; const m = Math.floor(n.length / 2); return Num(n.length % 2 ? n[m] : (n[m - 1] + n[m]) / 2); });
  F('MODE', (a, ev) => { const n = ev.numbers(a); const m = {}; let best = null, bc = 0; for (const x of n) { m[x] = (m[x] || 0) + 1; if (m[x] > bc) { bc = m[x]; best = x; } } return best == null ? NA : Num(best); });
  F('STDEV', (a, ev) => stdev(ev.numbers(a), true)); F('STDEVP', (a, ev) => stdev(ev.numbers(a), false));
  F('VAR', (a, ev) => variance(ev.numbers(a), true)); F('VARP', (a, ev) => variance(ev.numbers(a), false));
  F('LARGE', (a, ev) => { const n = ev.flatten(a[0]).filter(v => v.t === 'num').map(v => v.n).sort((x, y) => y - x); const k = asNum(ev.eval(a[1])); return (k >= 1 && k <= n.length) ? Num(n[k - 1]) : NUM; });
  F('SMALL', (a, ev) => { const n = ev.flatten(a[0]).filter(v => v.t === 'num').map(v => v.n).sort((x, y) => x - y); const k = asNum(ev.eval(a[1])); return (k >= 1 && k <= n.length) ? Num(n[k - 1]) : NUM; });
  F('COUNTIF', (a, ev) => { const crit = ev.eval(a[1]); let c = 0; for (const v of ev.flatten(a[0])) if (matches(v, crit)) c++; return Num(c); });
  F('SUMIF', (a, ev) => { const rng = ev.flatten(a[0]); const crit = ev.eval(a[1]); const sr = a.length > 2 ? ev.flatten(a[2]) : rng; let s = 0; for (let i = 0; i < rng.length; i++) if (matches(rng[i], crit) && i < sr.length) s += asNum(sr[i]); return Num(s); });
  F('AVERAGEIF', (a, ev) => { const rng = ev.flatten(a[0]); const crit = ev.eval(a[1]); const ar = a.length > 2 ? ev.flatten(a[2]) : rng; let s = 0, c = 0; for (let i = 0; i < rng.length; i++) if (matches(rng[i], crit) && i < ar.length) { s += asNum(ar[i]); c++; } return c ? Num(s / c) : DIV0; });
  // Logical
  F('IF', (a, ev) => { const cond = ev.eval(a[0]); if (isErr(cond)) return cond; return truthy(cond) ? ev.eval(a[1]) : (a.length > 2 ? ev.eval(a[2]) : Bool(false)); });
  F('IFS', (a, ev) => { for (let i = 0; i + 1 < a.length; i += 2) if (truthy(ev.eval(a[i]))) return ev.eval(a[i + 1]); return NA; });
  F('AND', (a, ev) => { let r = true; for (const x of a) for (const v of ev.flatten(x)) r = r && truthy(v); return Bool(r); });
  F('OR', (a, ev) => { let r = false; for (const x of a) for (const v of ev.flatten(x)) r = r || truthy(v); return Bool(r); });
  F('NOT', (a, ev) => Bool(!truthy(ev.eval(a[0]))));
  F('XOR', (a, ev) => { let c = 0; for (const x of a) for (const v of ev.flatten(x)) if (truthy(v)) c++; return Bool(c % 2 === 1); });
  F('TRUE', () => Bool(true)); F('FALSE', () => Bool(false));
  F('IFERROR', (a, ev) => { const v = ev.eval(a[0]); return isErr(v) ? ev.eval(a[1]) : v; });
  F('IFNA', (a, ev) => { const v = ev.eval(a[0]); return (v.t === 'err' && v.code === '#N/A') ? ev.eval(a[1]) : v; });
  F('SWITCH', (a, ev) => { const t = asStr(ev.eval(a[0])); let i = 1; for (; i + 1 < a.length; i += 2) if (asStr(ev.eval(a[i])).toLowerCase() === t.toLowerCase()) return ev.eval(a[i + 1]); return i < a.length ? ev.eval(a[i]) : NA; });
  F('CHOOSE', (a, ev) => { const i = asNum(ev.eval(a[0])); return (i >= 1 && i < a.length) ? ev.eval(a[i]) : VALUE; });
  // Lookup
  F('VLOOKUP', (a, ev) => { const k = ev.eval(a[0]); const rg = a[1]; const col = asNum(ev.eval(a[2])); const approx = a.length < 4 || truthy(ev.eval(a[3])); if (rg.k !== 'range') return NA; let best = -1; for (let r = rg.r1; r <= rg.r2; r++) { const v = ev.sheet.value(r, rg.c1); if (eq(v, k)) { const cc = rg.c1 + col - 1; return cc <= rg.c2 ? ev.sheet.value(r, cc) : REF; } if (approx && v.t === 'num' && k.t === 'num' && v.n <= k.n) best = r; } if (approx && best >= 0) { const cc = rg.c1 + col - 1; return cc <= rg.c2 ? ev.sheet.value(best, cc) : REF; } return NA; });
  F('HLOOKUP', (a, ev) => { const k = ev.eval(a[0]); const rg = a[1]; const row = asNum(ev.eval(a[2])); if (rg.k !== 'range') return NA; for (let c = rg.c1; c <= rg.c2; c++) { const v = ev.sheet.value(rg.r1, c); if (eq(v, k)) { const rr = rg.r1 + row - 1; return rr <= rg.r2 ? ev.sheet.value(rr, c) : REF; } } return NA; });
  F('INDEX', (a, ev) => { const rg = a[0]; if (rg.k !== 'range') return REF; const rn = asNum(ev.eval(a[1])); const cn = a.length > 2 ? asNum(ev.eval(a[2])) : 1; const rows = rg.r2 - rg.r1 + 1, cols = rg.c2 - rg.c1 + 1; if (rows === 1) { const c = rg.c1 + rn - 1; return c <= rg.c2 ? ev.sheet.value(rg.r1, c) : REF; } if (cols === 1) { const r = rg.r1 + rn - 1; return r <= rg.r2 ? ev.sheet.value(r, rg.c1) : REF; } const r = rg.r1 + rn - 1, c = rg.c1 + cn - 1; return (r <= rg.r2 && c <= rg.c2) ? ev.sheet.value(r, c) : REF; });
  F('MATCH', (a, ev) => { const k = ev.eval(a[0]); const rg = a[1]; const type = a.length > 2 ? asNum(ev.eval(a[2])) : 1; if (rg.k !== 'range') return NA; const vals = ev.flattenRange(rg); if (type === 0) { for (let i = 0; i < vals.length; i++) if (eq(vals[i], k)) return Num(i + 1); return NA; } let idx = -1; for (let i = 0; i < vals.length; i++) { if (vals[i].t === 'num' && k.t === 'num') { if (type === 1 && vals[i].n <= k.n) idx = i; if (type === -1 && vals[i].n >= k.n) idx = i; } } return idx >= 0 ? Num(idx + 1) : NA; });
  // Text
  F('CONCAT', (a, ev) => { let s = ''; for (const x of a) for (const v of ev.flatten(x)) s += asStr(v); return Str(s); });
  F('CONCATENATE', (a, ev) => { let s = ''; for (const x of a) s += asStr(ev.eval(x)); return Str(s); });
  F('TEXTJOIN', (a, ev) => { const d = asStr(ev.eval(a[0])); const skip = truthy(ev.eval(a[1])); const parts = []; for (let i = 2; i < a.length; i++) for (const v of ev.flatten(a[i])) { const s = asStr(v); if (!skip || s !== '') parts.push(s); } return Str(parts.join(d)); });
  F('LEFT', (a, ev) => { const s = asStr(ev.eval(a[0])); const n = a.length > 1 ? asNum(ev.eval(a[1])) : 1; return Str(s.slice(0, Math.max(0, n))); });
  F('RIGHT', (a, ev) => { const s = asStr(ev.eval(a[0])); const n = a.length > 1 ? asNum(ev.eval(a[1])) : 1; return Str(n <= 0 ? '' : s.slice(-n)); });
  F('MID', (a, ev) => { const s = asStr(ev.eval(a[0])); const st = asNum(ev.eval(a[1])) - 1; const len = asNum(ev.eval(a[2])); return (st < 0 || st >= s.length) ? Str('') : Str(s.substr(st, len)); });
  F('LEN', (a, ev) => Num(asStr(ev.eval(a[0])).length));
  F('TRIM', (a, ev) => Str(asStr(ev.eval(a[0])).trim().replace(/ +/g, ' ')));
  F('UPPER', (a, ev) => Str(asStr(ev.eval(a[0])).toUpperCase()));
  F('LOWER', (a, ev) => Str(asStr(ev.eval(a[0])).toLowerCase()));
  F('PROPER', (a, ev) => Str(asStr(ev.eval(a[0])).replace(/\w\S*/g, t => t[0].toUpperCase() + t.slice(1).toLowerCase())));
  F('REPT', (a, ev) => Str(asStr(ev.eval(a[0])).repeat(Math.max(0, Math.min(10000, asNum(ev.eval(a[1])))))));
  F('SUBSTITUTE', (a, ev) => { const s = asStr(ev.eval(a[0])); const o = asStr(ev.eval(a[1])); const nw = asStr(ev.eval(a[2])); return Str(s.split(o).join(nw)); });
  F('FIND', (a, ev) => { const sub = asStr(ev.eval(a[0])); const s = asStr(ev.eval(a[1])); const i = s.indexOf(sub); return i < 0 ? VALUE : Num(i + 1); });
  F('SEARCH', (a, ev) => { const sub = asStr(ev.eval(a[0])).toLowerCase(); const s = asStr(ev.eval(a[1])).toLowerCase(); const i = s.indexOf(sub); return i < 0 ? VALUE : Num(i + 1); });
  F('VALUE', (a, ev) => { const d = parseFloat(asStr(ev.eval(a[0])).replace(/,/g, '')); return isNaN(d) ? VALUE : Num(d); });
  F('TEXT', (a, ev) => Str(asStr(ev.eval(a[0]))));
  F('EXACT', (a, ev) => Bool(asStr(ev.eval(a[0])) === asStr(ev.eval(a[1]))));
  // Info
  F('ISBLANK', (a, ev) => Bool(ev.eval(a[0]).t === 'blank'));
  F('ISNUMBER', (a, ev) => Bool(ev.eval(a[0]).t === 'num'));
  F('ISTEXT', (a, ev) => Bool(ev.eval(a[0]).t === 'str'));
  F('ISLOGICAL', (a, ev) => Bool(ev.eval(a[0]).t === 'bool'));
  F('ISERROR', (a, ev) => Bool(isErr(ev.eval(a[0]))));
  F('ISNA', (a, ev) => { const v = ev.eval(a[0]); return Bool(v.t === 'err' && v.code === '#N/A'); });
  F('N', (a, ev) => Num(asNum(ev.eval(a[0]))));
  F('NA', () => NA);
  // Financial
  F('PMT', (a, ev) => { const r = asNum(ev.eval(a[0])), n = asNum(ev.eval(a[1])), pv = asNum(ev.eval(a[2])), fv = a.length > 3 ? asNum(ev.eval(a[3])) : 0; return r === 0 ? Num(-(pv + fv) / n) : Num(-(pv * Math.pow(1 + r, n) + fv) * r / (Math.pow(1 + r, n) - 1)); });
  F('FV', (a, ev) => { const r = asNum(ev.eval(a[0])), n = asNum(ev.eval(a[1])), pmt = asNum(ev.eval(a[2])), pv = a.length > 3 ? asNum(ev.eval(a[3])) : 0; return r === 0 ? Num(-(pv + pmt * n)) : Num(-(pv * Math.pow(1 + r, n) + pmt * (Math.pow(1 + r, n) - 1) / r)); });
  F('PV', (a, ev) => { const r = asNum(ev.eval(a[0])), n = asNum(ev.eval(a[1])), pmt = asNum(ev.eval(a[2])), fv = a.length > 3 ? asNum(ev.eval(a[3])) : 0; return r === 0 ? Num(-(fv + pmt * n)) : Num(-(fv + pmt * (Math.pow(1 + r, n) - 1) / r) / Math.pow(1 + r, n)); });
  F('NPV', (a, ev) => { const r = asNum(ev.eval(a[0])); let s = 0, t = 1; for (let i = 1; i < a.length; i++) for (const v of ev.flatten(a[i])) { s += asNum(v) / Math.pow(1 + r, t); t++; } return Num(s); });

  // ---- extra math ----
  F('GCD', (a, ev) => { const n = ev.numbers(a).map(x => Math.abs(Math.trunc(x))); const g = (x, y) => y ? g(y, x % y) : x; return Num(n.reduce((x, y) => g(x, y), 0)); });
  F('LCM', (a, ev) => { const n = ev.numbers(a).map(x => Math.abs(Math.trunc(x))); const g = (x, y) => y ? g(y, x % y) : x; return Num(n.reduce((x, y) => (x && y) ? x / g(x, y) * y : 0, 1)); });
  F('FACT', (a, ev) => { let x = Math.trunc(asNum(ev.eval(a[0]))); if (x < 0) return NUM; let r = 1; for (let i = 2; i <= x; i++) r *= i; return Num(r); });
  F('COMBIN', (a, ev) => { const n = Math.trunc(asNum(ev.eval(a[0]))), k = Math.trunc(asNum(ev.eval(a[1]))); if (k < 0 || k > n) return NUM; let r = 1; for (let i = 0; i < k; i++) r = r * (n - i) / (i + 1); return Num(Math.round(r)); });
  F('PERMUT', (a, ev) => { const n = Math.trunc(asNum(ev.eval(a[0]))), k = Math.trunc(asNum(ev.eval(a[1]))); if (k < 0 || k > n) return NUM; let r = 1; for (let i = 0; i < k; i++) r *= (n - i); return Num(r); });
  F('QUOTIENT', (a, ev) => { const d = asNum(ev.eval(a[1])); if (d === 0) return DIV0; return Num(Math.trunc(asNum(ev.eval(a[0])) / d)); });
  F('EVEN', (a, ev) => { const x = asNum(ev.eval(a[0])); return Num(Math.sign(x || 1) * Math.ceil(Math.abs(x) / 2) * 2); });
  F('ODD', (a, ev) => { const x = asNum(ev.eval(a[0])); const s = Math.sign(x || 1); let r = Math.ceil(Math.abs(x)); if (r % 2 === 0) r++; return Num(s * r); });
  F('MROUND', (a, ev) => { const x = asNum(ev.eval(a[0])), m = asNum(ev.eval(a[1])); return m === 0 ? Num(0) : Num(Math.round(x / m) * m); });
  F('SINH', (a, ev) => Num(Math.sinh(asNum(ev.eval(a[0]))))); F('COSH', (a, ev) => Num(Math.cosh(asNum(ev.eval(a[0]))))); F('TANH', (a, ev) => Num(Math.tanh(asNum(ev.eval(a[0])))));
  // ---- multi-criteria aggregates ----
  function ifsLoop(a, ev, start) { const first = ev.flatten(a[start]); const n = first.length; const keep = []; for (let i = 0; i < n; i++) { let ok = true; for (let j = start + 1; j + 1 < a.length; j += 2) { const cr = ev.flatten(a[j]); if (i >= cr.length || !matches(cr[i], ev.eval(a[j + 1]))) { ok = false; break; } } if (ok) keep.push(i); } return { first, keep }; }
  F('SUMIFS', (a, ev) => { const { first, keep } = ifsLoop(a, ev, 0); let s = 0; for (const i of keep) s += asNum(first[i]); return Num(s); });
  F('COUNTIFS', (a, ev) => { const first = ev.flatten(a[0]); let c = 0; for (let i = 0; i < first.length; i++) { let ok = true; for (let j = 0; j + 1 < a.length; j += 2) { const cr = ev.flatten(a[j]); if (i >= cr.length || !matches(cr[i], ev.eval(a[j + 1]))) { ok = false; break; } } if (ok) c++; } return Num(c); });
  F('AVERAGEIFS', (a, ev) => { const { first, keep } = ifsLoop(a, ev, 0); if (!keep.length) return DIV0; let s = 0; for (const i of keep) s += asNum(first[i]); return Num(s / keep.length); });
  F('MAXIFS', (a, ev) => { const { first, keep } = ifsLoop(a, ev, 0); let m = -Infinity; for (const i of keep) m = Math.max(m, asNum(first[i])); return Num(keep.length ? m : 0); });
  F('MINIFS', (a, ev) => { const { first, keep } = ifsLoop(a, ev, 0); let m = Infinity; for (const i of keep) m = Math.min(m, asNum(first[i])); return Num(keep.length ? m : 0); });
  // ---- extra stats ----
  F('AVERAGEA', (a, ev) => { let s = 0, c = 0; for (const x of a) for (const v of ev.flatten(x)) { if (v.t === 'blank') continue; s += asNum(v); c++; } return c ? Num(s / c) : DIV0; });
  F('GEOMEAN', (a, ev) => { const n = ev.numbers(a); if (!n.length) return NUM; let p = 1; for (const x of n) { if (x <= 0) return NUM; p *= x; } return Num(Math.pow(p, 1 / n.length)); });
  F('RANK', (a, ev) => { const v = asNum(ev.eval(a[0])); const arr = ev.flatten(a[1]).filter(x => x.t === 'num').map(x => x.n); const asc = a.length > 2 && truthy(ev.eval(a[2])); let r = 1; for (const x of arr) { if (asc ? x < v : x > v) r++; } return Num(r); });
  F('PERCENTILE', (a, ev) => { const n = ev.flatten(a[0]).filter(x => x.t === 'num').map(x => x.n).sort((x, y) => x - y); if (!n.length) return NUM; const p = asNum(ev.eval(a[1])); const idx = p * (n.length - 1); const lo = Math.floor(idx), hi = Math.ceil(idx); return Num(n[lo] + (n[hi] - n[lo]) * (idx - lo)); });
  F('QUARTILE', (a, ev) => { const n = ev.flatten(a[0]).filter(x => x.t === 'num').map(x => x.n).sort((x, y) => x - y); if (!n.length) return NUM; const q = asNum(ev.eval(a[1])); const p = q / 4; const idx = p * (n.length - 1); const lo = Math.floor(idx), hi = Math.ceil(idx); return Num(n[lo] + (n[hi] - n[lo]) * (idx - lo)); });
  // ---- reference ----
  F('ROWS', (a) => { const rg = a[0]; return rg && rg.k === 'range' ? Num(rg.r2 - rg.r1 + 1) : Num(1); });
  F('COLUMNS', (a) => { const rg = a[0]; return rg && rg.k === 'range' ? Num(rg.c2 - rg.c1 + 1) : Num(1); });
  F('ROW', (a, ev) => { if (!a.length) return Num((ev.curR || 0) + 1); const rg = a[0]; return Num((rg.k === 'range' ? rg.r1 : (rg.k === 'ref' ? rg.r : 0)) + 1); });
  F('COLUMN', (a, ev) => { if (!a.length) return Num((ev.curC || 0) + 1); const rg = a[0]; return Num((rg.k === 'range' ? rg.c1 : (rg.k === 'ref' ? rg.c : 0)) + 1); });
  F('LOOKUP', (a, ev) => { const k = ev.eval(a[0]); const rg = a[1]; if (rg.k !== 'range') return NA; const vals = ev.flattenRange(rg); let idx = -1; for (let i = 0; i < vals.length; i++) { if (vals[i].t === 'num' && k.t === 'num' ? vals[i].n <= k.n : asStr(vals[i]).toLowerCase() <= asStr(k).toLowerCase()) idx = i; } if (idx < 0) return NA; if (a.length > 2 && a[2].k === 'range') { const res = ev.flattenRange(a[2]); return idx < res.length ? res[idx] : NA; } return vals[idx]; });
  F('XLOOKUP', (a, ev) => { const k = ev.eval(a[0]); const lr = a[1], rr = a[2]; if (lr.k !== 'range' || rr.k !== 'range') return NA; const lv = ev.flattenRange(lr), rv = ev.flattenRange(rr); for (let i = 0; i < lv.length; i++) if (eq(lv[i], k)) return i < rv.length ? rv[i] : NA; return a.length > 3 ? ev.eval(a[3]) : NA; });
  // ---- extra text ----
  F('REPLACE', (a, ev) => { const s = asStr(ev.eval(a[0])); const st = asNum(ev.eval(a[1])) - 1; const len = asNum(ev.eval(a[2])); const nw = asStr(ev.eval(a[3])); return Str(s.slice(0, st) + nw + s.slice(st + len)); });
  F('CHAR', (a, ev) => Str(String.fromCharCode(asNum(ev.eval(a[0]))))); F('CODE', (a, ev) => { const s = asStr(ev.eval(a[0])); return s ? Num(s.charCodeAt(0)) : VALUE; });
  F('UNICHAR', (a, ev) => Str(String.fromCodePoint(asNum(ev.eval(a[0]))))); F('UNICODE', (a, ev) => { const s = asStr(ev.eval(a[0])); return s ? Num(s.codePointAt(0)) : VALUE; });
  F('CLEAN', (a, ev) => Str(asStr(ev.eval(a[0])).replace(/[\x00-\x1F\x7F]/g, '')));
  F('FIXED', (a, ev) => { const x = asNum(ev.eval(a[0])); const d = a.length > 1 ? asNum(ev.eval(a[1])) : 2; const noComma = a.length > 2 && truthy(ev.eval(a[2])); const s = x.toFixed(Math.max(0, d)); return Str(noComma ? s : (+s).toLocaleString('en-IN', { minimumFractionDigits: Math.max(0, d), maximumFractionDigits: Math.max(0, d) })); });
  F('NUMBERVALUE', (a, ev) => { const d = parseFloat(asStr(ev.eval(a[0])).replace(/,/g, '')); return isNaN(d) ? VALUE : Num(d); });
  F('T', (a, ev) => { const v = ev.eval(a[0]); return v.t === 'str' ? v : Str(''); });
  // ---- date & time (serial = days since 1899-12-30) ----
  const EPOCH = Date.UTC(1899, 11, 30);
  const toSerial = (ms) => (ms - EPOCH) / 86400000;
  const fromSerial = (n) => new Date(EPOCH + Math.round(n * 86400000));
  F('TODAY', () => { const d = new Date(); return Num(Math.floor(toSerial(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate())))); });
  F('NOW', () => { const d = new Date(); return Num(toSerial(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds()))); });
  F('DATE', (a, ev) => Num(Math.floor(toSerial(Date.UTC(asNum(ev.eval(a[0])), asNum(ev.eval(a[1])) - 1, asNum(ev.eval(a[2])))))));
  F('YEAR', (a, ev) => Num(fromSerial(asNum(ev.eval(a[0]))).getUTCFullYear()));
  F('MONTH', (a, ev) => Num(fromSerial(asNum(ev.eval(a[0]))).getUTCMonth() + 1));
  F('DAY', (a, ev) => Num(fromSerial(asNum(ev.eval(a[0]))).getUTCDate()));
  F('HOUR', (a, ev) => { const f = asNum(ev.eval(a[0])) % 1; return Num(Math.floor(f * 24 + 1e-9)); });
  F('MINUTE', (a, ev) => { const f = asNum(ev.eval(a[0])) % 1; return Num(Math.floor((f * 24 % 1) * 60 + 1e-9)); });
  F('SECOND', (a, ev) => { const f = asNum(ev.eval(a[0])) % 1; return Num(Math.round((f * 24 * 60 % 1) * 60)); });
  F('WEEKDAY', (a, ev) => { const wd = fromSerial(asNum(ev.eval(a[0]))).getUTCDay(); const type = a.length > 1 ? asNum(ev.eval(a[1])) : 1; if (type === 2) return Num(wd === 0 ? 7 : wd); if (type === 3) return Num(wd === 0 ? 6 : wd - 1); return Num(wd + 1); });
  F('EDATE', (a, ev) => { const d = fromSerial(asNum(ev.eval(a[0]))); const m = asNum(ev.eval(a[1])); return Num(Math.floor(toSerial(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + m, d.getUTCDate())))); });
  F('EOMONTH', (a, ev) => { const d = fromSerial(asNum(ev.eval(a[0]))); const m = asNum(ev.eval(a[1])); return Num(Math.floor(toSerial(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + m + 1, 0)))); });
  F('DAYS', (a, ev) => Num(Math.floor(asNum(ev.eval(a[0])) - asNum(ev.eval(a[1])))));
  F('DATEVALUE', (a, ev) => { const t = Date.parse(asStr(ev.eval(a[0]))); return isNaN(t) ? VALUE : Num(Math.floor(toSerial(t))); });
  F('DATEDIF', (a, ev) => { const s = fromSerial(asNum(ev.eval(a[0]))), e = fromSerial(asNum(ev.eval(a[1]))); const u = asStr(ev.eval(a[2])).toUpperCase(); if (u === 'D') return Num(Math.round((e - s) / 86400000)); if (u === 'Y') { let y = e.getUTCFullYear() - s.getUTCFullYear(); if (e.getUTCMonth() < s.getUTCMonth() || (e.getUTCMonth() === s.getUTCMonth() && e.getUTCDate() < s.getUTCDate())) y--; return Num(y); } if (u === 'M') { let m = (e.getUTCFullYear() - s.getUTCFullYear()) * 12 + (e.getUTCMonth() - s.getUTCMonth()); if (e.getUTCDate() < s.getUTCDate()) m--; return Num(m); } return VALUE; });
  // ---- extra financial ----
  F('NPER', (a, ev) => { const r = asNum(ev.eval(a[0])), pmt = asNum(ev.eval(a[1])), pv = asNum(ev.eval(a[2])), fv = a.length > 3 ? asNum(ev.eval(a[3])) : 0; if (r === 0) return pmt === 0 ? NUM : Num(-(pv + fv) / pmt); const v = (pmt - fv * r) / (pmt + pv * r); return v <= 0 ? NUM : Num(Math.log(v) / Math.log(1 + r)); });
  F('RATE', (a, ev) => { const n = asNum(ev.eval(a[0])), pmt = asNum(ev.eval(a[1])), pv = asNum(ev.eval(a[2])), fv = a.length > 3 ? asNum(ev.eval(a[3])) : 0; let r = 0.1; for (let i = 0; i < 80; i++) { const f = Math.pow(1 + r, n); const val = pv * f + pmt * (f - 1) / r + fv; const d = n * pv * Math.pow(1 + r, n - 1) + pmt * ((n * Math.pow(1 + r, n - 1) * r - (f - 1)) / (r * r)); const nr = r - val / d; if (!isFinite(nr)) break; if (Math.abs(nr - r) < 1e-8) return Num(nr); r = nr; } return Num(r); });
  F('IRR', (a, ev) => { const cf = ev.flatten(a[0]).map(asNum); let r = a.length > 1 ? asNum(ev.eval(a[1])) : 0.1; for (let it = 0; it < 80; it++) { let npv = 0, d = 0; for (let t = 0; t < cf.length; t++) { npv += cf[t] / Math.pow(1 + r, t); d += -t * cf[t] / Math.pow(1 + r, t + 1); } const nr = r - npv / d; if (!isFinite(nr)) break; if (Math.abs(nr - r) < 1e-8) return Num(nr); r = nr; } return Num(r); });
  F('SLN', (a, ev) => { const c = asNum(ev.eval(a[0])), s = asNum(ev.eval(a[1])), l = asNum(ev.eval(a[2])); return l === 0 ? DIV0 : Num((c - s) / l); });
  F('SYD', (a, ev) => { const c = asNum(ev.eval(a[0])), s = asNum(ev.eval(a[1])), l = asNum(ev.eval(a[2])), p = asNum(ev.eval(a[3])); return Num((c - s) * (l - p + 1) * 2 / (l * (l + 1))); });
  // ---- extra info ----
  F('ISEVEN', (a, ev) => Bool(Math.trunc(asNum(ev.eval(a[0]))) % 2 === 0));
  F('ISODD', (a, ev) => Bool(Math.abs(Math.trunc(asNum(ev.eval(a[0]))) % 2) === 1));
  F('ISNONTEXT', (a, ev) => Bool(ev.eval(a[0]).t !== 'str'));
  F('TYPE', (a, ev) => { const v = ev.eval(a[0]); return Num(v.t === 'num' ? 1 : v.t === 'str' ? 2 : v.t === 'bool' ? 4 : isErr(v) ? 16 : 1); });

  function eq(a, b) { if (a.t === 'num' && b.t === 'num') return a.n === b.n; return asStr(a).toLowerCase() === asStr(b).toLowerCase(); }
  function stdev(n, sample) { if (n.length < (sample ? 2 : 1)) return DIV0; const m = n.reduce((x, y) => x + y, 0) / n.length; const ss = n.reduce((x, y) => x + (y - m) * (y - m), 0); return Num(Math.sqrt(ss / (sample ? n.length - 1 : n.length))); }
  function variance(n, sample) { if (n.length < (sample ? 2 : 1)) return DIV0; const m = n.reduce((x, y) => x + y, 0) / n.length; const ss = n.reduce((x, y) => x + (y - m) * (y - m), 0); return Num(ss / (sample ? n.length - 1 : n.length)); }

  const API = { Sheet, colName, colIndex, parseRef, MAX_ROWS, MAX_COLS, asStr, asNum, Functions, _types: { Num, Str, Bool } };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  global.BrahmSheet = API;
})(typeof window !== 'undefined' ? window : globalThis);
