// BRAHM Office — Android WebView UI (PDF via pdf.js, Excel via SheetJS + engine)
// Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
(function () {
  const E = window.BrahmSheet;
  const AB = window.AndroidBridge || null;
  try { pdfjsLib.GlobalWorkerOptions.workerSrc = 'vendor/pdf.worker.min.js'; } catch (e) {}

  let book = [new E.Sheet('Sheet1')];
  let active = 0;
  const sheet = () => book[active];

  // ---- per-cell formatting (display only; kept in-app) ----
  const cellKey = (r, c) => r * E.MAX_COLS + c;
  function fmtOf(s) { if (!s.fmt) s.fmt = {}; return s.fmt; }
  function fmtGet(s, r, c) { return fmtOf(s)[cellKey(r, c)]; }
  function fmtSet(prop, val) {
    const f = fmtOf(sheet()); const k = cellKey(selR, selC);
    const o = f[k] || {}; o[prop] = val; f[k] = o; draw();
  }
  function fmtClear() { const f = fmtOf(sheet()); delete f[cellKey(selR, selC)]; draw(); }
  function formatNum(n, nf) {
    switch (nf) {
      case 'num': return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      case 'inr': return '₹ ' + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      case 'pct': return (n * 100).toFixed(2) + '%';
      case 'date': { const d = new Date(Date.UTC(1899, 11, 30) + n * 86400000); return isNaN(d) ? String(n) : d.toLocaleDateString('en-GB'); }
      default: return null;
    }
  }

  const canvas = document.getElementById('grid');
  const ctx = canvas.getContext('2d');
  const pdfview = document.getElementById('pdfview');
  const textview = document.getElementById('textview');
  const htmlview = document.getElementById('htmlview');
  const wordview = document.getElementById('wordview');
  const loading = document.getElementById('loading');
  const fbar = document.getElementById('fbar');
  const zoombar = document.getElementById('zoombar');
  const cellref = document.getElementById('cellref');
  const finput = document.getElementById('finput');
  const aclist = document.getElementById('aclist');
  const loupe = document.getElementById('loupe');
  const loupecv = document.getElementById('loupecv');
  const lctx = loupecv.getContext('2d');

  let dpr = window.devicePixelRatio || 1;
  const BASE_W = 92, BASE_H = 30;
  let gridZoom = 1;
  let cellW = BASE_W, cellH = BASE_H, rowHead = 46, colHead = 28;
  let scrollX = 0, scrollY = 0, selR = 0, selC = 0, selR2 = 0, selC2 = 0;
  let rangeMode = false, clipRange = null;
  const selBox = () => ({ r1: Math.min(selR, selR2), c1: Math.min(selC, selC2), r2: Math.max(selR, selR2), c2: Math.max(selC, selC2) });
  const hasRange = () => selR2 !== selR || selC2 !== selC;
  function cellAtXY(x, y) { if (x < rowHead || y < colHead) return null; const c = Math.floor((scrollX + x - rowHead) / cellW), r = Math.floor((scrollY + y - colHead) / cellH); if (r < 0 || r >= E.MAX_ROWS || c < 0 || c >= E.MAX_COLS) return null; return { r, c }; }
  function fillHandleScreen() { const b = selBox(); return { x: rowHead + (b.c2 + 1) * cellW - scrollX, y: colHead + (b.r2 + 1) * cellH - scrollY }; }

  // ---- conditional formatting ----
  function cfFor(r, c, v) {
    const rules = sheet().cf; if (!rules || !rules.length) return null; let out = null;
    for (const rule of rules) { if (r < rule.r1 || r > rule.r2 || c < rule.c1 || c > rule.c2) continue; if (cfTest(rule, v)) out = { bg: rule.bg, fg: rule.fg }; }
    return out;
  }
  function cfTest(rule, v) {
    const num = (v && v.t === 'num') ? v.n : null; const str = E.asStr(v || { t: 'blank' }); const tv = String(rule.valText == null ? '' : rule.valText).toLowerCase();
    switch (rule.op) {
      case 'gt': return num != null && num > rule.val; case 'lt': return num != null && num < rule.val;
      case 'ge': return num != null && num >= rule.val; case 'le': return num != null && num <= rule.val;
      case 'eq': return (num != null && num === rule.val) || str.toLowerCase() === tv;
      case 'between': return num != null && num >= rule.val && num <= rule.val2;
      case 'contains': return tv !== '' && str.toLowerCase().includes(tv);
      case 'notempty': return str.trim() !== '';
    } return false;
  }

  // ---- fill handle: copy source cell across the dragged rectangle, adjusting relative refs ----
  function shiftFormula(input, dr, dc) {
    if (!input || input[0] !== '=') return input;
    return input.replace(/(?<![A-Za-z0-9_$])(\$?)([A-Za-z]{1,2})(\$?)(\d{1,6})(?![A-Za-z0-9(])/g, (full, ad, colL, ar, rowD) => {
      const c = E.colIndex(colL); if (c < 0 || c >= E.MAX_COLS) return full; const r = parseInt(rowD, 10) - 1;
      const nc = ad ? c : c + dc, nr = ar ? r : r + dr; if (nc < 0 || nr < 0 || nc >= E.MAX_COLS || nr >= E.MAX_ROWS) return '#REF!';
      return (ad ? '$' : '') + E.colName(nc) + (ar ? '$' : '') + (nr + 1);
    });
  }
  function doFill() {
    const b = selBox(); if (!hasRange()) return; snapshot(); const s = sheet();
    const sr = selR, sc = selC, srcInput = s.raw(sr, sc), srcFmt = Object.assign({}, fmtGet(s, sr, sc) || {});
    for (let r = b.r1; r <= b.r2; r++) for (let c = b.c1; c <= b.c2; c++) {
      if (r === sr && c === sc) continue;
      s.set(r, c, shiftFormula(srcInput, r - sr, c - sc));
      if (Object.keys(srcFmt).length) fmtOf(s)[cellKey(r, c)] = Object.assign({}, srcFmt); else delete fmtOf(s)[cellKey(r, c)];
    }
    draw(); setStatus('Filled ' + ((b.r2 - b.r1 + 1) * (b.c2 - b.c1 + 1)) + ' cells');
  }

  // ---------- view switching ----------
  function hideAll() {
    canvas.style.display = 'none'; pdfview.style.display = 'none';
    textview.style.display = 'none'; htmlview.style.display = 'none'; wordview.style.display = 'none';
    fbar.style.display = 'none'; zoombar.classList.remove('show'); zoombar.classList.remove('pdfmode'); aclist.classList.remove('show');
    const fb = document.getElementById('fmtbar'); if (fb) fb.classList.remove('show');
    const pt = document.getElementById('pdftools'); if (pt) pt.classList.remove('show');
    const tt = document.getElementById('texttools'); if (tt) tt.classList.remove('show');
    const wt = document.getElementById('wordtools'); if (wt) wt.classList.remove('show');
    const tabsEl = document.getElementById('tabs'); if (tabsEl) tabsEl.style.display = 'none';   // sheet tabs only in sheet view
    if (editMode) { editMode = false; const eb = document.getElementById('pdfEdit'); if (eb) eb.classList.remove('on'); }
    pdfview.innerHTML = ''; htmlview.src = 'about:blank';
    document.getElementById('gallery').classList.remove('show');
    document.getElementById('lightbox').classList.remove('show');
    hideLoupe();
  }
  let currentView = 'sheet', textFontSize = 15, htmlZoom = 1;
  function showSheet() { hideAll(); currentView = 'sheet'; canvas.style.display = 'block'; fbar.style.display = 'flex'; const tabsEl = document.getElementById('tabs'); if (tabsEl) tabsEl.style.display = 'flex'; resize(); draw(); }
  function showLoading(on) { loading.classList.toggle('show', on); }
  function setStatus(s) { document.getElementById('status').textContent = s + ' · Developed by Sandeep Kushwah'; }

  // ---------- grid draw ----------
  function applyZoom() { cellW = Math.round(BASE_W * gridZoom); cellH = Math.round(BASE_H * gridZoom); }
  function resize() {
    dpr = window.devicePixelRatio || 1;
    const r = canvas.getBoundingClientRect();
    canvas.width = Math.floor(r.width * dpr); canvas.height = Math.floor(r.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0); draw();
  }
  window.addEventListener('resize', resize);
  const viewW = () => canvas.width / dpr - rowHead;
  const viewH = () => canvas.height / dpr - colHead;
  const maxSX = () => Math.max(0, E.MAX_COLS * cellW - viewW());
  const maxSY = () => Math.max(0, E.MAX_ROWS * cellH - viewH());
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

  let curFs = 13;
  let freezeOn = false;
  function drawCellAt(r, c, left, top) {
    const f = fmtGet(sheet(), r, c);
    const v = sheet().value(r, c);
    const cf = cfFor(r, c, v);
    ctx.fillStyle = (cf && cf.bg) ? cf.bg : ((f && f.fill) ? f.fill : ((r % 2 === 0) ? '#fff' : '#fafbfc'));
    ctx.fillRect(left, top, cellW, cellH);
    if (r === selR && c === selC) { ctx.fillStyle = 'rgba(31,157,99,.18)'; ctx.fillRect(left, top, cellW, cellH); }
    ctx.strokeStyle = '#dce1e8'; ctx.lineWidth = 1; ctx.strokeRect(left + .5, top + .5, cellW, cellH);
    const isNum = v && v.t === 'num';
    let t = (f && f.nf && isNum) ? formatNum(v.n, f.nf) : sheet().display(r, c);
    if (t) { ctx.save(); ctx.beginPath(); ctx.rect(left, top, cellW, cellH); ctx.clip();
      ctx.font = ((f && f.i) ? 'italic ' : '') + ((f && f.b) ? 'bold ' : '') + curFs + 'px sans-serif';
      ctx.fillStyle = (v && v.t === 'err') ? '#D64541' : ((cf && cf.fg) ? cf.fg : ((f && f.color) ? f.color : '#16233F'));
      if (isNum) { ctx.textAlign = 'right'; ctx.fillText(t, left + cellW - 6, top + cellH * .68); }
      else { ctx.textAlign = 'left'; ctx.fillText(t, left + 6, top + cellH * .68); }
      ctx.restore(); ctx.font = curFs + 'px sans-serif'; }
    // data-validation ▾ marker (bottom-right) and comment triangle (top-right)
    const s = sheet();
    if (s.dv && s.dv[cellKey(r, c)]) { ctx.fillStyle = '#2B579A'; ctx.beginPath(); ctx.moveTo(left + cellW - 11, top + cellH - 6); ctx.lineTo(left + cellW - 3, top + cellH - 6); ctx.lineTo(left + cellW - 7, top + cellH - 1); ctx.fill(); }
    if (s.comments && s.comments[cellKey(r, c)]) { ctx.fillStyle = '#D64541'; ctx.beginPath(); ctx.moveTo(left + cellW - 7, top + 1); ctx.lineTo(left + cellW - 1, top + 1); ctx.lineTo(left + cellW - 1, top + 7); ctx.fill(); }
  }
  function draw() {
    if (canvas.style.display === 'none') return;
    const w = canvas.width / dpr, h = canvas.height / dpr;
    ctx.clearRect(0, 0, w, h);
    curFs = Math.max(11, Math.round(13 * gridZoom));
    ctx.font = curFs + 'px sans-serif';
    const fC = Math.max(0, Math.floor(scrollX / cellW));
    const lC = Math.min(E.MAX_COLS - 1, Math.floor((scrollX + viewW()) / cellW));
    const fR = Math.max(0, Math.floor(scrollY / cellH));
    const lR = Math.min(E.MAX_ROWS - 1, Math.floor((scrollY + viewH()) / cellH));
    for (let r = fR; r <= lR; r++) { const top = colHead + r * cellH - scrollY;
      for (let c = fC; c <= lC; c++) drawCellAt(r, c, rowHead + c * cellW - scrollX, top); }
    { const b = selBox();
      if (hasRange()) { const l = rowHead + b.c1 * cellW - scrollX, t = colHead + b.r1 * cellH - scrollY, w2 = (b.c2 - b.c1 + 1) * cellW, h2 = (b.r2 - b.r1 + 1) * cellH;
        ctx.fillStyle = 'rgba(31,157,99,.12)'; ctx.fillRect(l, t, w2, h2); ctx.strokeStyle = '#1F9D63'; ctx.lineWidth = 2; ctx.strokeRect(l + 1, t + 1, w2 - 2, h2 - 2); }
      if (selR >= fR && selR <= lR && selC >= fC && selC <= lC) { const l = rowHead + selC * cellW - scrollX, t = colHead + selR * cellH - scrollY;
        ctx.strokeStyle = '#1F9D63'; ctx.lineWidth = 2; ctx.strokeRect(l + 1, t + 1, cellW - 2, cellH - 2); }
      const hx = rowHead + (b.c2 + 1) * cellW - scrollX, hy = colHead + (b.r2 + 1) * cellH - scrollY;   // fill handle
      if (hx > rowHead && hy > colHead && hx < w + 8 && hy < h + 8) { ctx.fillStyle = '#1F9D63'; ctx.fillRect(hx - 5, hy - 5, 9, 9); ctx.strokeStyle = '#fff'; ctx.lineWidth = 1; ctx.strokeRect(hx - 5.5, hy - 5.5, 10, 10); }
    }
    if (freezeOn) {   // pinned first row + first column
      for (let c = fC; c <= lC; c++) if (c !== 0) drawCellAt(0, c, rowHead + c * cellW - scrollX, colHead);
      for (let r = fR; r <= lR; r++) if (r !== 0) drawCellAt(r, 0, rowHead, colHead + r * cellH - scrollY);
      drawCellAt(0, 0, rowHead, colHead);
    }
    ctx.fillStyle = '#16233F'; ctx.fillRect(0, 0, w, colHead);
    ctx.textAlign = 'center';
    for (let c = fC; c <= lC; c++) { const left = rowHead + c * cellW - scrollX;
      if (c === selC) { ctx.fillStyle = '#E8A33D'; ctx.fillRect(left, 0, cellW, colHead); ctx.fillStyle = '#2a1d00'; } else ctx.fillStyle = '#fff';
      ctx.fillText(E.colName(c), left + cellW / 2, colHead * .68); }
    ctx.fillStyle = '#16233F'; ctx.fillRect(0, 0, rowHead, h);
    for (let r = fR; r <= lR; r++) { const top = colHead + r * cellH - scrollY;
      if (r === selR) { ctx.fillStyle = '#E8A33D'; ctx.fillRect(0, top, rowHead, cellH); ctx.fillStyle = '#2a1d00'; } else ctx.fillStyle = '#fff';
      ctx.fillText(String(r + 1), rowHead / 2, top + cellH * .68); }
    ctx.fillStyle = '#0E1830'; ctx.fillRect(0, 0, rowHead, colHead);
    if (freezeOn) { ctx.fillStyle = 'rgba(232,163,61,.9)'; ctx.fillRect(rowHead, colHead - 2, w, 2); ctx.fillRect(rowHead - 2, colHead, 2, h); }
  }

  // ---------- grid gestures: drag-scroll, tap-select, double-tap zoom, long-press magnify ----------
  let gStartX = 0, gStartY = 0, gSX = 0, gSY = 0, gMoved = false, gScroll = false, gLong = null, gMag = false;
  let gLastTap = 0, gLastX = 0, gLastY = 0;
  let gPinch = false, gd0 = 0, gz0 = 1, gFillDrag = false, gRangeDrag = false;
  let gVX = 0, gVY = 0, gLastT = 0, gLastMX = 0, gLastMY = 0, gMomentum = null;
  const gDist = (ts) => Math.hypot(ts[0].clientX - ts[1].clientX, ts[0].clientY - ts[1].clientY);
  function stopMomentum() { if (gMomentum) { cancelAnimationFrame(gMomentum); gMomentum = null; } }
  function startMomentum() {
    let vx = gVX * 16, vy = gVY * 16;
    if (Math.abs(vx) < 0.6 && Math.abs(vy) < 0.6) return;
    const step = () => {
      vx *= 0.93; vy *= 0.93;
      if (Math.abs(vx) < 0.3 && Math.abs(vy) < 0.3) { gMomentum = null; return; }
      scrollX = clamp(scrollX + vx, 0, maxSX()); scrollY = clamp(scrollY + vy, 0, maxSY()); draw();
      gMomentum = requestAnimationFrame(step);
    };
    gMomentum = requestAnimationFrame(step);
  }

  canvas.addEventListener('touchstart', (e) => {
    stopMomentum();
    if (e.touches.length === 2) { gPinch = true; clearTimeout(gLong); gMag = false; hideLoupe(); gd0 = gDist(e.touches) || 1; gz0 = gridZoom; return; }
    const t = e.touches[0];
    gStartX = t.clientX; gStartY = t.clientY; gSX = scrollX; gSY = scrollY;
    gMoved = false; gScroll = false; gMag = false; gFillDrag = false; gRangeDrag = false;
    gVX = 0; gVY = 0; gLastT = performance.now(); gLastMX = t.clientX; gLastMY = t.clientY;
    const rect = canvas.getBoundingClientRect(), lx = t.clientX - rect.left, ly = t.clientY - rect.top;
    const hp = fillHandleScreen();
    if (Math.abs(lx - hp.x) <= 24 && Math.abs(ly - hp.y) <= 24) { gFillDrag = true; return; }   // grab fill handle
    if (rangeMode) { const cell = cellAtXY(lx, ly); if (cell) { select(cell.r, cell.c); gRangeDrag = true; draw(); return; } }
    gLong = setTimeout(() => { gMag = true; showLoupeAt(canvas, t.clientX, t.clientY); }, 380);
  }, { passive: true });

  canvas.addEventListener('touchmove', (e) => {
    if (gPinch) { if (e.touches.length === 2) { if (e.cancelable) e.preventDefault();
        gridZoom = Math.max(0.5, Math.min(3, gz0 * gDist(e.touches) / gd0));
        applyZoom(); scrollX = clamp(scrollX, 0, maxSX()); scrollY = clamp(scrollY, 0, maxSY()); draw(); } return; }
    const t = e.touches[0];
    if (gFillDrag || gRangeDrag) { if (e.cancelable) e.preventDefault(); const rect = canvas.getBoundingClientRect(); const cell = cellAtXY(t.clientX - rect.left, t.clientY - rect.top); if (cell) { selR2 = cell.r; selC2 = cell.c; draw(); } return; }
    const dx = gStartX - t.clientX, dy = gStartY - t.clientY;
    if (gMag) { showLoupeAt(canvas, t.clientX, t.clientY); if (e.cancelable) e.preventDefault(); return; }
    if (gScroll || Math.abs(dx) + Math.abs(dy) > 6) { gMoved = true; gScroll = true; clearTimeout(gLong);
      const now = performance.now(), ddt = now - gLastT;
      if (ddt > 0) { gVX = (gLastMX - t.clientX) / ddt; gVY = (gLastMY - t.clientY) / ddt; }
      gLastT = now; gLastMX = t.clientX; gLastMY = t.clientY;
      if (e.cancelable) e.preventDefault();
      scrollX = clamp(gSX + dx, 0, maxSX()); scrollY = clamp(gSY + dy, 0, maxSY()); draw(); }
  }, { passive: false });

  canvas.addEventListener('touchend', (e) => {
    clearTimeout(gLong);
    if (gFillDrag) { gFillDrag = false; doFill(); return; }
    if (gRangeDrag) { gRangeDrag = false; const b = selBox(); const n = (b.r2 - b.r1 + 1) * (b.c2 - b.c1 + 1); let sum = 0, cnt = 0;
      for (let r = b.r1; r <= b.r2; r++) for (let c = b.c1; c <= b.c2; c++) { const v = sheet().value(r, c); if (v && v.t === 'num') { sum += v.n; cnt++; } }
      setStatus('Range ' + E.colName(b.c1) + (b.r1 + 1) + ':' + E.colName(b.c2) + (b.r2 + 1) + ' · ' + n + ' cells' + (cnt ? ' · Sum ' + (Math.round(sum * 100) / 100) + ' · Avg ' + (Math.round(sum / cnt * 100) / 100) : '')); return; }
    if (gPinch) { if (e.touches.length < 2) gPinch = false; return; }
    if (gMag) { gMag = false; hideLoupe(); return; }
    if (gScroll || gMoved) { startMomentum(); return; }
    const r = canvas.getBoundingClientRect();
    const x = gStartX - r.left, y = gStartY - r.top;
    const now = Date.now();
    if (now - gLastTap < 300 && Math.abs(x - gLastX) < 30 && Math.abs(y - gLastY) < 30) {
      gLastTap = 0; cycleGridZoom(); return;    // double-tap = zoom
    }
    gLastTap = now; gLastX = x; gLastY = y;
    if (x >= rowHead && y >= colHead) {
      const c = Math.floor((scrollX + x - rowHead) / cellW), row = Math.floor((scrollY + y - colHead) / cellH);
      if (row >= 0 && row < E.MAX_ROWS && c >= 0 && c < E.MAX_COLS) { select(row, c); showDVChooser(row, c); }
    }
  }, { passive: true });

  function cycleGridZoom() {
    gridZoom = gridZoom >= 1.9 ? 1 : (gridZoom >= 1.4 ? 2 : 1.5);
    applyZoom(); scrollX = clamp(scrollX, 0, maxSX()); scrollY = clamp(scrollY, 0, maxSY()); draw();
  }

  function select(r, c) { commit(); selR = r; selC = c; selR2 = r; selC2 = c; cellref.textContent = E.colName(c) + (r + 1); finput.value = sheet().raw(r, c); aclist.classList.remove('show'); updateFmtUI(); ensureVis(); draw(); const cm = (sheet().comments || {})[cellKey(r, c)]; if (cm) setStatus('📝 ' + cm); }
  function updateFmtUI() {
    const f = fmtGet(sheet(), selR, selC) || {};
    const fB = document.getElementById('fB'), fI = document.getElementById('fI'), fNF = document.getElementById('fNF');
    if (fB) fB.classList.toggle('on', !!f.b);
    if (fI) fI.classList.toggle('on', !!f.i);
    if (fNF) fNF.value = f.nf || 'gen';
  }
  function ensureVis() { const cl = selC * cellW, ct = selR * cellH;
    if (cl < scrollX) scrollX = cl; else if (cl + cellW > scrollX + viewW()) scrollX = cl + cellW - viewW();
    if (ct < scrollY) scrollY = ct; else if (ct + cellH > scrollY + viewH()) scrollY = ct + cellH - viewH();
    scrollX = clamp(scrollX, 0, maxSX()); scrollY = clamp(scrollY, 0, maxSY()); }

  // ---------- undo ----------
  const undoStack = [];
  function snapshot() {
    const snap = book.map(s => ({ name: s.name, cells: Array.from(s.cells.entries()).map(([k, v]) => [k, v.input]) }));
    undoStack.push({ active, data: snap }); if (undoStack.length > 60) undoStack.shift();
  }
  function restore(st) {
    book = st.data.map(sd => { const s = new E.Sheet(sd.name); sd.cells.forEach(([k, inp]) => s.cells.set(k, { input: inp, ast: undefined })); s.clearCache && s.clearCache(); return s; });
    active = Math.min(st.active, book.length - 1); renderTabs(); draw(); select(selR, selC);
  }
  document.getElementById('btnUndo').onclick = () => { if (undoStack.length) { restore(undoStack.pop()); setStatus('Undo'); } };

  function commit() {
    const raw = finput.value;
    if (raw !== sheet().raw(selR, selC)) { snapshot(); sheet().set(selR, selC, raw); }
  }
  finput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { commit(); aclist.classList.remove('show'); select(Math.min(E.MAX_ROWS - 1, selR + 1), selC); e.preventDefault(); } });
  finput.addEventListener('blur', () => { setTimeout(() => aclist.classList.remove('show'), 150); commit(); });

  // ---------- function autocomplete ----------
  const FN = (E.Functions.names ? E.Functions.names() : []).slice().sort();
  const FN_HINT = { SUM:'add a range', AVERAGE:'mean', IF:'condition', VLOOKUP:'look up', COUNT:'count numbers', COUNTA:'count non-empty', MAX:'largest', MIN:'smallest', ROUND:'round number', SUMIF:'sum with condition', COUNTIF:'count with condition', CONCAT:'join text', LEFT:'left chars', RIGHT:'right chars', LEN:'length', TODAY:"today's date", INDEX:'value at position', MATCH:'find position' };
  finput.addEventListener('input', () => {
    const val = finput.value;
    if (!val.startsWith('=')) { aclist.classList.remove('show'); return; }
    const m = /([A-Za-z]+)$/.exec(val);
    const token = m ? m[1].toUpperCase() : '';
    if (!token) { aclist.classList.remove('show'); return; }
    const matches = FN.filter(f => f.startsWith(token)).slice(0, 12);
    if (!matches.length) { aclist.classList.remove('show'); return; }
    aclist.innerHTML = '';
    matches.forEach(fn => {
      const d = document.createElement('div'); d.className = 'acitem';
      d.innerHTML = '<span class="fn">' + fn + '(</span>' + (FN_HINT[fn] ? '<span class="hint">' + FN_HINT[fn] + '</span>' : '');
      d.addEventListener('mousedown', (ev) => { ev.preventDefault(); insertFn(fn); });
      aclist.appendChild(d);
    });
    aclist.classList.add('show');
  });
  function insertFn(fn) {
    finput.value = finput.value.replace(/([A-Za-z]+)$/, fn + '(');
    aclist.classList.remove('show'); finput.focus();
    const L = finput.value.length; try { finput.setSelectionRange(L, L); } catch (e) {}
  }

  // ---------- tabs: add / delete (long-press) / rename (double-tap) ----------
  function renderTabs() {
    const tdiv = document.getElementById('tabs'); tdiv.innerHTML = '';
    book.forEach((s, i) => {
      const d = document.createElement('div'); d.className = 'sheettab' + (i === active ? ' active' : ''); d.textContent = s.name;
      let lp = null, lastTab = 0;
      d.addEventListener('touchstart', () => { lp = setTimeout(() => { lp = null; deleteSheet(i); }, 500); }, { passive: true });
      d.addEventListener('touchend', () => {
        if (lp) { clearTimeout(lp); lp = null;
          const now = Date.now();
          if (now - lastTab < 300) { renameSheet(i); lastTab = 0; }
          else { lastTab = now; active = i; renderTabs(); select(0, 0); }
        }
      }, { passive: true });
      d.onclick = () => {};  // handled via touch
      tdiv.appendChild(d);
    });
    const add = document.createElement('div'); add.className = 'sheettab'; add.textContent = '+ Sheet';
    add.onclick = () => { snapshot(); book.push(new E.Sheet('Sheet' + (book.length + 1))); active = book.length - 1; renderTabs(); select(0, 0); setStatus('Sheet added'); };
    tdiv.appendChild(add);
  }
  function deleteSheet(i) {
    if (book.length <= 1) { setStatus('At least one sheet is needed'); return; }
    if (!confirm('Delete sheet "' + book[i].name + '"?')) return;
    snapshot(); book.splice(i, 1); if (active >= book.length) active = book.length - 1; renderTabs(); select(0, 0); setStatus('Sheet deleted');
  }
  function renameSheet(i) {
    const n = prompt('Rename sheet', book[i].name); if (n && n.trim()) { snapshot(); book[i].name = n.trim().slice(0, 31); renderTabs(); }
  }

  // ---------- magnifier loupe ----------
  function showLoupeAt(srcCanvas, cx, cy) {
    const rect = srcCanvas.getBoundingClientRect();
    if (cx < rect.left || cx > rect.right || cy < rect.top || cy > rect.bottom) { hideLoupe(); return; }
    const sx = (cx - rect.left) * (srcCanvas.width / rect.width);
    const sy = (cy - rect.top) * (srcCanvas.height / rect.height);
    const zoom = 2.4;
    const regionCss = loupecv.width / zoom;
    const regionSrc = regionCss * (srcCanvas.width / rect.width);
    lctx.fillStyle = '#fff'; lctx.fillRect(0, 0, loupecv.width, loupecv.height);
    try {
      lctx.drawImage(srcCanvas, sx - regionSrc / 2, sy - regionSrc / 2, regionSrc, regionSrc, 0, 0, loupecv.width, loupecv.height);
    } catch (e) {}
    // crosshair
    lctx.strokeStyle = 'rgba(31,157,99,.6)'; lctx.lineWidth = 1;
    lctx.beginPath(); lctx.moveTo(75, 60); lctx.lineTo(75, 90); lctx.moveTo(60, 75); lctx.lineTo(90, 75); lctx.stroke();
    let lx = cx - 75, ly = cy - 170;
    if (ly < 4) ly = cy + 24;
    loupe.style.left = Math.max(4, Math.min(window.innerWidth - 154, lx)) + 'px';
    loupe.style.top = ly + 'px';
    loupe.style.display = 'block';
  }
  function hideLoupe() { loupe.style.display = 'none'; }

  // ---------- PDF ----------
  let pdfDoc = null, pdfRenderScale = 1, pdfBaseW = 0, pdfBaseH = 0;
  let pdfBytes = null, pdfName = 'document.pdf', pageMeta = [], editMode = false, editTool = 'pen', pdfPassword = '';
  async function openPdf(bytes, name) {
    hideAll(); currentView = 'pdf'; pdfview.style.display = 'block'; zoombar.classList.add('show'); zoombar.classList.add('pdfmode'); showLoading(true);
    pdfBytes = bytes.slice(0); pdfName = name || 'document.pdf'; pdfPassword = '';
    if (editMode) toggleEdit(false);
    let doc = null, pw = '';
    for (let attempt = 0; attempt < 5; attempt++) {
      try {
        const data = pdfBytes.slice(0);
        doc = await pdfjsLib.getDocument(pw ? { data, password: pw } : { data }).promise;
        break;
      } catch (e) {
        if (e && (e.name === 'PasswordException' || e.code === 1 || e.code === 2)) {
          const entered = prompt((e.code === 2 ? 'Wrong password. ' : '') + 'This PDF is locked. Enter password:');
          if (entered === null) { showLoading(false); setStatus('Password needed'); showText('🔒 This PDF is password-protected.'); return; }
          pw = entered; continue;
        }
        showLoading(false); setStatus('PDF error'); showText('Could not open this PDF.'); return;
      }
    }
    if (!doc) { showLoading(false); setStatus('Wrong password'); showText('🔒 Could not unlock this PDF.'); return; }
    pdfPassword = pw; pdfDoc = doc;
    await renderPdf();
    setStatus('PDF · ' + pdfDoc.numPages + ' pages · pinch zoom · ✏️ Edit to annotate');
    showLoading(false);
    runPendingAuto();
  }
  // A tool chosen on the home "PDF tools" screen is auto-run once the file is open.
  function runPendingAuto() {
    const a = window.__brahmAuto; if (!a) return; window.__brahmAuto = null;
    setTimeout(() => { try { brahmAuto(a); } catch (e) {} }, 350);
  }
  function brahmAuto(tool) {
    const click = (id) => { const el = document.getElementById(id); if (el) el.click(); };
    switch (tool) {
      case 'pwadd': click('pdfLock'); break;
      case 'pwremove': click('pdfLock'); setTimeout(() => click('pwRemove'), 250); break;
      case 'convert': click('pdfConvert'); break;
      case 'toword': click('cv2wordOcr'); break;
      case 'towordlook': click('cv2wordLook'); break;
      case 'totext': click('cv2text'); break;
      case 'toimages': click('cv2imgs'); break;
      case 'img2pdf': click('cvImg2pdf'); break;
      case 'pages': click('pdfPages'); break;
      case 'ocr': click('pdfOcr'); break;
      case 'edit': toggleEdit(true); break;
      case 'search': { const s = document.getElementById('pdfSearch'); if (s) s.focus(); break; }
    }
  }
  window.BrahmAuto = function (tool) { window.__brahmAuto = tool; };   // set before/with the file open
  window.BrahmNew = function (type) {
    const t = (type || '').toLowerCase();
    if (t === 'pdf') { newBlankPdf(); }
    else if (t === 'word' || t === 'doc') { showWord('<p><br></p>', 'Untitled.docx'); }
    else if (t === 'sheet' || t === 'excel') { openBook([new E.Sheet('Sheet1')], 'New spreadsheet'); }
    else { openBook([new E.Sheet('Sheet1')], 'New spreadsheet'); }
  };
  // pdf-lib load options — ignoreEncryption lets owner/permission-encrypted PDFs (e.g. bank statements) load & save.
  function loadOpts(pw) { const o = { ignoreEncryption: true }; if (pw) o.password = pw; return o; }
  function savePdfOut(nm, b64) {
    if (AB && AB.saveFile) AB.saveFile(nm, 'application/pdf', b64);
    else { const bin = atob(b64); const arr = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([arr], { type: 'application/pdf' })); a.download = nm; a.click(); }
  }
  // ---- PDF password: set / remove (via @cantoo/pdf-lib) ----
  const pwmodal = document.getElementById('pwmodal');
  document.getElementById('pdfLock').onclick = () => { if (pdfBytes) pwmodal.classList.add('show'); else setStatus('Open a PDF first'); };
  document.getElementById('pwClose').onclick = () => pwmodal.classList.remove('show');
  pwmodal.addEventListener('click', (e) => { if (e.target === pwmodal) pwmodal.classList.remove('show'); });
  document.getElementById('pwSet').onclick = async () => {
    pwmodal.classList.remove('show');
    if (typeof PDFLib === 'undefined' || !pdfBytes) return;
    const pw = prompt('Set a password for this PDF:'); if (!pw) return;
    showLoading(true); setStatus('Encrypting…');
    try {
      const { PDFDocument } = PDFLib;
      const doc = await PDFDocument.load(pdfBytes.slice(0), loadOpts(pdfPassword));
      doc.encrypt({ userPassword: pw, ownerPassword: pw });
      const out = await doc.saveAsBase64();
      const nm = pdfName.replace(/\.pdf$/i, '') + '-protected.pdf';
      savePdfOut(nm, out); setStatus('Saved ' + nm + ' — password set');
    } catch (e) { setStatus('Encrypt failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('pwRemove').onclick = async () => {
    pwmodal.classList.remove('show');
    if (typeof PDFLib === 'undefined' || !pdfBytes) return;
    showLoading(true); setStatus('Removing password…');
    try {
      const { PDFDocument } = PDFLib;
      let doc;
      try { doc = await PDFDocument.load(pdfBytes.slice(0), loadOpts(pdfPassword)); }
      catch (e1) { const entered = prompt('Enter the current password to remove it:'); if (!entered) { showLoading(false); return; } doc = await PDFDocument.load(pdfBytes.slice(0), loadOpts(entered)); }
      const out = await doc.saveAsBase64();
      const nm = pdfName.replace(/\.pdf$/i, '') + '-unlocked.pdf';
      savePdfOut(nm, out); setStatus('Saved ' + nm + ' — password removed');
    } catch (e) { setStatus('Could not remove password: ' + e.message); }
    showLoading(false);
  };
  async function renderPdf() {
    pdfview.innerHTML = '<div id="pdfinner"></div>';
    pdfHits = []; pdfHitIdx = -1; pcssScale = 1; pageMeta = [];
    const inner = document.getElementById('pdfinner');
    const width = pdfview.clientWidth || window.innerWidth;
    const os = Math.min(window.devicePixelRatio || 1, 2);   // device-pixel oversample (proven, reliable)
    for (let n = 1; n <= pdfDoc.numPages; n++) {
      const page = await pdfDoc.getPage(n);
      const base = page.getViewport({ scale: 1 });
      const scale = (width - 16) / base.width * pdfRenderScale;
      const vp = page.getViewport({ scale });                  // CSS-size viewport (for layout + text layer)
      const vpHi = page.getViewport({ scale: scale * os });    // hi-res viewport baked into the canvas (no transform option)
      const cssW = Math.floor(vp.width), cssH = Math.floor(vp.height);
      const wrap = document.createElement('div'); wrap.className = 'pdfpage';
      const cv = document.createElement('canvas'); const c2 = cv.getContext('2d');
      cv.width = Math.floor(vpHi.width); cv.height = Math.floor(vpHi.height);
      cv.style.width = cssW + 'px'; cv.style.height = cssH + 'px';
      wrap.appendChild(cv); inner.appendChild(wrap);
      try { await page.render({ canvasContext: c2, viewport: vpHi }).promise; } catch (re) {}
      try { await buildTextLayer(page, vp, wrap); } catch (e) {}
      // Editing draws straight onto this same page canvas (one canvas only) — stacking a 2nd
      // canvas made some Android WebViews fail to composite the page beneath it (blank white).
      pageMeta.push({ pageNum: n, cssW, cssH, pdfW: base.width, pdfH: base.height, annot: [], pageCanvas: cv, wrap, scale, os, pxRatio: os, renderedZoom: 1 });
      bindDraw(cv, pageMeta.length - 1);
    }
    inner.style.zoom = ''; pcssScale = 1;
    if (pdfSearchEl && pdfSearchEl.value.trim()) runPdfSearch(pdfSearchEl.value.trim());
  }
  // finger-anchored zoom via CSS `zoom` — keeps native scroll (so pages always render) and no white-out
  function setPdfZoom(ns, cx, cy) {
    const inner = document.getElementById('pdfinner');
    if (!inner) return;
    ns = Math.max(0.5, Math.min(6, ns));
    const r = pdfview.getBoundingClientRect();
    if (cx == null) cx = r.left + r.width / 2;
    if (cy == null) cy = r.top + r.height / 2;
    const old = pcssScale || 1;
    const px = (pdfview.scrollLeft + (cx - r.left)) / old;   // point under finger in unscaled coords
    const py = (pdfview.scrollTop + (cy - r.top)) / old;
    pcssScale = ns;
    inner.style.zoom = ns;
    pdfview.scrollLeft = px * ns - (cx - r.left);
    pdfview.scrollTop = py * ns - (cy - r.top);
    scheduleSharpen();
  }
  async function buildTextLayer(page, vp, wrap) {
    const tc = await page.getTextContent();
    const layer = document.createElement('div'); layer.className = 'textlayer';
    layer.style.width = Math.floor(vp.width) + 'px'; layer.style.height = Math.floor(vp.height) + 'px';
    for (const item of tc.items) {
      if (!item.str) continue;
      const tx = pdfjsLib.Util.transform(vp.transform, item.transform);
      const fs = Math.hypot(tx[2], tx[3]);
      if (fs < 1) continue;
      const span = document.createElement('span');
      span.textContent = item.str;
      span.style.left = tx[4] + 'px';
      span.style.top = (tx[5] - fs) + 'px';
      span.style.fontSize = fs + 'px';
      const st = tc.styles[item.fontName]; if (st && st.fontFamily) span.style.fontFamily = st.fontFamily;
      span.addEventListener('click', function () { editTextSpan(span); });
      layer.appendChild(span);
    }
    wrap.appendChild(layer);
  }

  // ---------- PDF search ----------
  let pdfHits = [], pdfHitIdx = -1;
  const pdfSearchEl = document.getElementById('pdfSearch');
  const pdfHitsEl = document.getElementById('pdfHits');
  function clearHl() { pdfview.querySelectorAll('.textlayer span.hl,.textlayer span.hlcur').forEach(s => s.classList.remove('hl', 'hlcur')); }
  function runPdfSearch(q) {
    clearHl(); pdfHits = []; pdfHitIdx = -1;
    if (!q) { pdfHitsEl.textContent = ''; return; }
    const needle = q.toLowerCase();
    pdfview.querySelectorAll('.textlayer span').forEach(sp => {
      if (sp.textContent && sp.textContent.toLowerCase().includes(needle)) { sp.classList.add('hl'); pdfHits.push(sp); }
    });
    pdfHitsEl.textContent = pdfHits.length ? ('1/' + pdfHits.length) : '0/0';
    if (pdfHits.length) gotoHit(0);
  }
  function gotoHit(i) {
    if (!pdfHits.length) return;
    if (pdfHitIdx >= 0 && pdfHits[pdfHitIdx]) pdfHits[pdfHitIdx].classList.remove('hlcur');
    pdfHitIdx = (i + pdfHits.length) % pdfHits.length;
    const sp = pdfHits[pdfHitIdx]; sp.classList.add('hlcur');
    sp.scrollIntoView({ block: 'center', behavior: 'smooth' });
    pdfHitsEl.textContent = (pdfHitIdx + 1) + '/' + pdfHits.length;
  }
  let searchTimer = null;
  pdfSearchEl.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => runPdfSearch(pdfSearchEl.value.trim()), 300); });
  pdfSearchEl.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); if (pdfHits.length) gotoHit(pdfHitIdx + 1); else runPdfSearch(pdfSearchEl.value.trim()); } });
  document.getElementById('pdfNext').onclick = () => gotoHit(pdfHitIdx + 1);
  document.getElementById('pdfPrev').onclick = () => gotoHit(pdfHitIdx - 1);
  function doZoom(dir) {
    if (currentView === 'pdf') { if (pdfDoc) setPdfZoom(pcssScale + dir * 0.35, null, null, true); }
    else if (currentView === 'text') { textFontSize = clamp(textFontSize + dir * 2, 9, 44); textview.style.fontSize = textFontSize + 'px'; }
    else if (currentView === 'html') { htmlZoom = clamp(htmlZoom + dir * 0.15, 0.5, 3); htmlview.style.zoom = htmlZoom; }
    else if (currentView === 'word') { wordZoom = clamp(wordZoom + dir * 0.1, 0.6, 2.4); const p = wordview.querySelector('.page'); if (p) p.style.fontSize = (16 * wordZoom) + 'px'; }
  }
  document.getElementById('zoomIn').onclick = () => doZoom(1);
  document.getElementById('zoomOut').onclick = () => doZoom(-1);

  // PDF gestures: double-tap zoom + long-press magnify
  function pdfCanvasAt(x, y) { let el = document.elementFromPoint(x, y); if (!el) return null; if (el.tagName === 'CANVAS') return el; const wrap = el.closest && el.closest('.pdfpage'); return wrap ? wrap.querySelector('canvas') : null; }
  let pLong = null, pMag = false, pLastTap = 0, pLX = 0, pLY = 0, pStartX = 0, pStartY = 0;
  pdfview.addEventListener('touchstart', (e) => {
    if (e.touches.length > 1) return;
    const t = e.touches[0]; pStartX = t.clientX; pStartY = t.clientY; pMag = false;
    pLong = setTimeout(() => { pMag = true; const el = pdfCanvasAt(t.clientX, t.clientY); if (el) showLoupeAt(el, t.clientX, t.clientY); }, 420);
  }, { passive: true });
  pdfview.addEventListener('touchmove', (e) => {
    const t = e.touches[0];
    if (pMag) { const el = pdfCanvasAt(t.clientX, t.clientY); if (el) showLoupeAt(el, t.clientX, t.clientY); if (e.cancelable) e.preventDefault(); return; }
    if (Math.abs(pStartX - t.clientX) + Math.abs(pStartY - t.clientY) > 10) clearTimeout(pLong);
  }, { passive: false });
  pdfview.addEventListener('touchend', (e) => {
    clearTimeout(pLong);
    if (pMag) { pMag = false; hideLoupe(); return; }
    if (pinching || pinchJustEnded) return;
    const now = Date.now();
    if (now - pLastTap < 300 && Math.abs(pStartX - pLX) < 40 && Math.abs(pStartY - pLY) < 40) {
      pLastTap = 0; setPdfZoom(pcssScale > 1.4 ? 1 : 2.2, pStartX, pStartY, true);   // double-tap zoom toggle at tap point
    } else { pLastTap = now; pLX = pStartX; pLY = pStartY; }
  }, { passive: true });

  // PDF pinch-to-zoom — live CSS transform, anchored to the fingers, no re-render (so no blink / jump)
  let pinching = false, pinchJustEnded = false, pd0 = 0, ps0 = 1, pcssScale = 1;
  const pinchDist = (ts) => Math.hypot(ts[0].clientX - ts[1].clientX, ts[0].clientY - ts[1].clientY);
  pdfview.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 2) return;
    pinching = true; clearTimeout(pLong); hideLoupe();
    pd0 = pinchDist(e.touches) || 1; ps0 = pcssScale;
  }, { passive: true });
  pdfview.addEventListener('touchmove', (e) => {
    if (!pinching || e.touches.length !== 2) return;
    if (e.cancelable) e.preventDefault();
    const mx = (e.touches[0].clientX + e.touches[1].clientX) / 2, my = (e.touches[0].clientY + e.touches[1].clientY) / 2;
    setPdfZoom(ps0 * pinchDist(e.touches) / pd0, mx, my, false);
  }, { passive: false });
  pdfview.addEventListener('touchend', (e) => {
    if (!pinching || e.touches.length >= 2) return;
    pinching = false; pinchJustEnded = true; setTimeout(() => pinchJustEnded = false, 400);
  }, { passive: true });

  // ---------- PDF editing (pen / highlight / text → save via pdf-lib) ----------
  const pdftools = document.getElementById('pdftools');
  let lastPage = 0, drawing = false, curStroke = null;
  function natXY(cv, meta, clientX, clientY) { const r = cv.getBoundingClientRect(); return { x: (clientX - r.left) * (meta.cssW / r.width), y: (clientY - r.top) * (meta.cssH / r.height) }; }
  // Draw directly on the page canvas (natural coords scaled up by meta.os to the hi-res bitmap).
  function strokeSeg(meta, a, p0, p1) {
    const k = meta.pxRatio || meta.os; const g = meta.pageCanvas.getContext('2d'); g.save(); g.setTransform(k, 0, 0, k, 0, 0);
    g.globalAlpha = a.type === 'hl' ? 0.3 : 1; g.strokeStyle = a.color; g.lineWidth = a.w; g.lineCap = 'round'; g.lineJoin = 'round';
    g.beginPath(); g.moveTo(p0.x, p0.y); g.lineTo(p1.x, p1.y); g.stroke(); g.restore();
  }
  function drawTextAnnot(meta, a) {
    const k = meta.pxRatio || meta.os; const g = meta.pageCanvas.getContext('2d'); g.save(); g.setTransform(k, 0, 0, k, 0, 0);
    g.globalAlpha = 1; g.fillStyle = a.color; g.font = a.size + 'px sans-serif'; g.textBaseline = 'top';
    (a.str || '').split('\n').forEach((ln, i) => g.fillText(ln, a.x, a.y + i * a.size * 1.2)); g.restore();
  }
  // --- Unicode (Latin + Devanagari) font embed, so edited/added text saves correctly in Hindi too ---
  function fontBytes() {
    if (!window.BRAHM_FONT_B64) return null;
    try { const bin = atob(window.BRAHM_FONT_B64); const u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; } catch (e) { return null; }
  }
  async function embedUniFont(doc) {
    try {
      const fb = fontBytes();
      // subset:false — subsetting onto a LOADED pdf drops most glyphs (renders only a few letters); full embed is reliable.
      if (fb && window.fontkit && doc.registerFontkit) { doc.registerFontkit(window.fontkit); return await doc.embedFont(fb, { subset: false }); }
    } catch (e) {}
    return null; // caller falls back to a standard font
  }
  // background colour under a text box (text is usually darker than its background → pick the lightest sample)
  function sampleBg(meta, x, y, w, h) {
    try {
      const k = meta.pxRatio || meta.os; const g = meta.pageCanvas.getContext('2d');
      const pts = [[x + w + 2, y + h * 0.5], [x - 3, y + h * 0.5], [x + w * 0.5, y - 3], [x + w * 0.5, y + h + 2]];
      let best = null, bestSum = -1;
      for (const p of pts) {
        const sx = Math.round(p[0] * k), sy = Math.round(p[1] * k);
        if (sx < 0 || sy < 0 || sx >= meta.pageCanvas.width || sy >= meta.pageCanvas.height) continue;
        const d = g.getImageData(sx, sy, 1, 1).data; const sum = d[0] + d[1] + d[2];
        if (sum > bestSum) { bestSum = sum; best = d; }
      }
      if (best) return '#' + [best[0], best[1], best[2]].map(v => v.toString(16).padStart(2, '0')).join('');
    } catch (e) {}
    return '#ffffff';
  }
  function measureTextW(size, str, fam) { const g = document.createElement('canvas').getContext('2d'); g.font = size + 'px ' + (fam || 'sans-serif'); return g.measureText(str || '').width; }
  function drawEditText(meta, a) {
    const k = meta.pxRatio || meta.os; const g = meta.pageCanvas.getContext('2d'); g.save(); g.setTransform(k, 0, 0, k, 0, 0);
    g.globalAlpha = 1; g.fillStyle = a.bg || '#ffffff'; g.fillRect(a.x - 1, a.y - 1, a.w + 2, a.h + 2);
    if (a.str) { g.fillStyle = a.color || '#000'; g.textBaseline = 'top'; g.font = a.size + 'px ' + (a.fontFamily || 'sans-serif'); g.fillText(a.str, a.x, a.y); }
    g.restore();
  }
  // Tapping a text span in "Edit text" mode replaces that run of real PDF text.
  // Many PDFs store one line/field as MANY tiny spans (per word or per glyph). Editing just one
  // leaves the rest of the original showing through → the "spaced-out characters" mess. So we
  // group the whole contiguous run the user tapped (same line, small gaps), edit it as one unit,
  // cover it with a single whiteout and redraw clean text.
  function spanGeo(s) { const size = parseFloat(s.style.fontSize) || 12; return { s, x: s.offsetLeft, y: s.offsetTop, w: s.offsetWidth, size, mid: s.offsetTop + size / 2 }; }
  function lineRun(span) {
    const layer = span.closest('.textlayer'); if (!layer) return [span];
    const all = Array.from(layer.querySelectorAll('span')).map(spanGeo);
    const t = spanGeo(span);
    // same line = vertical centres very close AND similar font size (so a dense bill doesn't merge rows/headers)
    const line = all.filter(g => Math.abs(g.mid - t.mid) <= t.size * 0.5 && g.size <= t.size * 1.4 && g.size >= t.size * 0.6).sort((a, b) => a.x - b.x);
    const idx = line.findIndex(g => g.s === span); if (idx < 0) return [span];
    const run = [line[idx]];
    const near = (a, b) => (b.x - (a.x + a.w)) <= t.size * 1.0;   // contiguous (within ~1 char); big gap = next column
    for (let i = idx - 1; i >= 0; i--) { if (near(line[i], run[0])) run.unshift(line[i]); else break; }
    for (let i = idx + 1; i < line.length; i++) { const last = run[run.length - 1]; if (near(last, line[i])) run.push(line[i]); else break; }
    return run.map(g => g.s);
  }
  function runText(spans) { // reconstruct words: one space only where there's a real gap
    let out = ''; let prev = null;
    for (const s of spans) { const g = spanGeo(s); const txt = (s.dataset.orig != null ? s.dataset.orig : s.textContent) || '';
      if (prev) { const gap = g.x - (prev.x + prev.w); const needSp = gap > g.size * 0.28 && !/\s$/.test(out) && !/^\s/.test(txt); if (needSp) out += ' '; }
      out += txt; prev = g; }
    return out.replace(/\s+/g, ' ').trim();
  }
  function editTextSpan(span) {
    if (!editMode || editTool !== 'edittext') return;
    const wrap = span.closest('.pdfpage'); const meta = pageMeta.find(m => m.wrap === wrap); if (!meta) return;
    const spans = lineRun(span);
    const cur = runText(spans);
    const val = prompt('Edit this text:', cur);
    if (val === null) return;
    // Font size = the TAPPED span's size (never the max — avoids a stray big span blowing it up).
    const tap = spanGeo(span); const size = tap.size; const fam = span.style.fontFamily || 'sans-serif';
    let x = Infinity, right = -Infinity;
    for (const s of spans) { const g = spanGeo(s); x = Math.min(x, g.x); right = Math.max(right, g.x + g.w); }
    const y = tap.y, h = size * 1.35;                 // one line only
    const w = Math.max(right - x, measureTextW(size, val, fam)) + 2;
    const bg = sampleBg(meta, x, y, w, h);
    const a = { type: 'edittext', x, y, w, h, size, str: val, color: '#000000', bg, fontFamily: fam };
    meta.annot.push(a); drawEditText(meta, a);
    // hide every span in the run from the text layer so nothing shows/searches twice
    for (const s of spans) { if (s.dataset.orig == null) s.dataset.orig = s.textContent; s.textContent = (s === spans[0]) ? val : ''; }
    setStatus('Text changed · Save PDF to keep it');
  }
  function paintAnnots(meta) {
    for (const a of meta.annot) {
      if (a.type === 'text') drawTextAnnot(meta, a);
      else if (a.type === 'edittext') drawEditText(meta, a);
      else { if (a.pts.length === 1) strokeSeg(meta, a, a.pts[0], a.pts[0]); for (let i = 1; i < a.pts.length; i++) strokeSeg(meta, a, a.pts[i - 1], a.pts[i]); }
    }
  }
  // Re-render a page's bitmap at the given zoom so text stays sharp (never below base res),
  // then repaint its annotations. Keeps ONE canvas, CSS size fixed at natural cssW (inner `zoom` scales it).
  const MAX_CW = 2600;
  async function renderPageAt(meta, z) {
    if (!pdfDoc) return;
    try {
      const page = await pdfDoc.getPage(meta.pageNum);
      let eff = meta.scale * meta.os * Math.max(1, z);
      let vp2 = page.getViewport({ scale: eff });
      if (vp2.width > MAX_CW) { eff = eff * MAX_CW / vp2.width; vp2 = page.getViewport({ scale: eff }); }
      const cv = meta.pageCanvas, g = cv.getContext('2d');
      cv.width = Math.floor(vp2.width); cv.height = Math.floor(vp2.height);
      cv.style.width = meta.cssW + 'px'; cv.style.height = meta.cssH + 'px';
      g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cv.width, cv.height);
      await page.render({ canvasContext: g, viewport: vp2 }).promise;
      meta.pxRatio = cv.width / meta.cssW; meta.renderedZoom = Math.max(1, z);
      if (meta.annot.length) paintAnnots(meta);
    } catch (e) {}
  }
  function redrawPage(meta) { return renderPageAt(meta, pcssScale); }
  // sharpen the pages currently on screen to match the live zoom (debounced)
  let sharpenTimer = null;
  function scheduleSharpen() { clearTimeout(sharpenTimer); sharpenTimer = setTimeout(sharpenPdf, 230); }
  async function sharpenPdf() {
    if (!pageMeta.length) return;
    const r = pdfview.getBoundingClientRect(); const z = Math.max(1, pcssScale);
    for (const m of pageMeta) {
      const pr = m.pageCanvas.getBoundingClientRect();
      if (pr.bottom > r.top - 60 && pr.top < r.bottom + 60 && m.renderedZoom !== z) await renderPageAt(m, pcssScale);
    }
  }
  pdfview.addEventListener('scroll', () => { if (pcssScale > 1) scheduleSharpen(); }, { passive: true });
  function bindDraw(cv, idx) {
    cv.addEventListener('touchstart', (e) => {
      if (!editMode || e.touches.length > 1) return;
      const meta = pageMeta[idx]; lastPage = idx;
      const p = natXY(cv, meta, e.touches[0].clientX, e.touches[0].clientY);
      e.stopPropagation(); if (e.cancelable) e.preventDefault();
      if (editTool === 'text') {
        const str = prompt('Text to place on page:');
        if (str && str.trim()) { const a = { type: 'text', x: p.x, y: p.y, color: pColorEl.value, size: 18, str: str }; meta.annot.push(a); drawTextAnnot(meta, a); }
        return;
      }
      drawing = true; curStroke = { type: editTool, color: pColorEl.value, w: +pSizeEl.value * (editTool === 'hl' ? 3 : 1), pts: [p] };
      meta.annot.push(curStroke); strokeSeg(meta, curStroke, p, p);
    }, { passive: false });
    cv.addEventListener('touchmove', (e) => {
      if (!editMode || !drawing || e.touches.length > 1 || !curStroke) return;
      e.stopPropagation(); if (e.cancelable) e.preventDefault();
      const meta = pageMeta[idx]; const p = natXY(cv, meta, e.touches[0].clientX, e.touches[0].clientY);
      const prev = curStroke.pts[curStroke.pts.length - 1]; curStroke.pts.push(p); strokeSeg(meta, curStroke, prev, p);
    }, { passive: false });
    cv.addEventListener('touchend', (e) => { if (!editMode) return; e.stopPropagation(); drawing = false; curStroke = null; }, { passive: false });
  }
  const pColorEl = document.getElementById('pColor'), pSizeEl = document.getElementById('pSize');
  function toggleEdit(on) {
    editMode = on; pdftools.classList.toggle('show', on);
    document.getElementById('pdfEdit').classList.toggle('on', on);
    if (!on || editTool !== 'edittext') pdfview.classList.remove('edittext');
    else pdfview.classList.add('edittext');
    setStatus(on ? 'Edit mode · draw, T to add text, ✎T to change real text, then Save PDF' : 'PDF · pinch zoom · search above');
  }
  document.getElementById('pdfEdit').onclick = () => toggleEdit(!editMode);
  document.getElementById('pDone').onclick = () => toggleEdit(false);
  document.querySelectorAll('.ptool').forEach(b => b.onclick = () => {
    document.querySelectorAll('.ptool').forEach(x => x.classList.remove('on')); b.classList.add('on'); editTool = b.getAttribute('data-tool');
    pdfview.classList.toggle('edittext', editMode && editTool === 'edittext');
    if (editTool === 'edittext') setStatus('Edit text · tap any highlighted text to change it');
  });
  document.getElementById('pUndo').onclick = () => {
    let m = pageMeta[lastPage];
    if (!m || !m.annot.length) m = pageMeta.find(x => x.annot.length);
    if (m && m.annot.length) { m.annot.pop(); redrawPage(m); }
  };
  document.getElementById('pClear').onclick = () => { pageMeta.forEach(m => { if (m.annot.length) { m.annot = []; redrawPage(m); } }); setStatus('Annotations cleared'); };
  function hexRgb(h) { const n = parseInt(h.replace('#', ''), 16); return { r: ((n >> 16) & 255) / 255, g: ((n >> 8) & 255) / 255, b: (n & 255) / 255 }; }
  // Paint one annotation onto any 2D context at scale k (k = canvasPx per CSS px).
  function paintAnnotOn(g, k, a) {
    g.save(); g.setTransform(k, 0, 0, k, 0, 0);
    if (a.type === 'edittext') {
      g.globalAlpha = 1; g.fillStyle = a.bg || '#ffffff'; g.fillRect(a.x - 1, a.y - 1, a.w + 2, a.h + 2);
      if (a.str) { g.fillStyle = a.color || '#000'; g.textBaseline = 'top'; g.font = a.size + 'px ' + (a.fontFamily || 'sans-serif'); g.fillText(a.str, a.x, a.y); }
    } else if (a.type === 'text') {
      g.globalAlpha = 1; g.fillStyle = a.color; g.textBaseline = 'top'; g.font = a.size + 'px sans-serif';
      (a.str || '').split('\n').forEach((ln, i) => g.fillText(ln, a.x, a.y + i * a.size * 1.2));
    } else {
      g.globalAlpha = a.type === 'hl' ? 0.3 : 1; g.strokeStyle = a.color; g.lineWidth = a.w; g.lineCap = 'round'; g.lineJoin = 'round';
      const p = a.pts || []; if (p.length) { g.beginPath(); g.moveTo(p[0].x, p[0].y); for (let i = 1; i < p.length; i++) g.lineTo(p[i].x, p[i].y); g.stroke(); }
    }
    g.restore();
  }
  // Flatten a page to a PNG (page bitmap + all edits baked in) — guaranteed WYSIWYG.
  async function renderFlatPng(meta) {
    const page = await pdfDoc.getPage(meta.pageNum);
    let eff = meta.scale * 2;                                   // 2× the fit scale → crisp
    let vp = page.getViewport({ scale: eff });
    if (vp.width > MAX_CW * 1.6) { eff = eff * (MAX_CW * 1.6) / vp.width; vp = page.getViewport({ scale: eff }); }
    const cv = document.createElement('canvas'); cv.width = Math.floor(vp.width); cv.height = Math.floor(vp.height);
    const g = cv.getContext('2d'); await page.render({ canvasContext: g, viewport: vp }).promise;
    const k = cv.width / meta.cssW;
    for (const a of meta.annot) paintAnnotOn(g, k, a);
    return { dataUrl: cv.toDataURL('image/png'), ptW: vp.width / eff, ptH: vp.height / eff };
  }
  function dataUrlToU8(u) { const b = atob(u.split(',')[1]); const a = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) a[i] = b.charCodeAt(i); return a; }
  document.getElementById('pSave').onclick = async () => {
    if (typeof PDFLib === 'undefined' || !pdfBytes) { setStatus('Editor unavailable'); return; }
    const hasAny = pageMeta.some(m => m.annot.length);
    if (!hasAny) { setStatus('Nothing to save — draw or add text first'); return; }
    showLoading(true); setStatus('Saving edited PDF…');
    try {
      const { PDFDocument, rgb, StandardFonts, LineCapStyle } = PDFLib;
      const doc = await PDFDocument.load(pdfBytes, loadOpts(pdfPassword));
      const uni = await embedUniFont(doc);                       // Latin + Devanagari (Hindi)
      const font = uni || await doc.embedFont(StandardFonts.Helvetica);
      const fallbackFont = uni ? await doc.embedFont(StandardFonts.Helvetica) : font;
      const drawTx = (pg, str, o) => { try { pg.drawText(str, { ...o, font }); } catch (e) { try { pg.drawText(str, { ...o, font: fallbackFont }); } catch (e2) {} } };
      const pages = doc.getPages();
      for (const meta of pageMeta) {
        const pg = pages[meta.pageNum - 1]; if (!pg) continue;
        // Use the CropBox (fallback MediaBox) so coordinates match even when the page origin ≠ (0,0).
        let box; try { box = pg.getCropBox(); } catch (e) { box = pg.getMediaBox(); }
        const ox = box.x, oy = box.y, bw = box.width, bh = box.height;
        const fx = bw / meta.cssW, fy = bh / meta.cssH;
        const X = (cx) => ox + cx * fx;                 // CSS-x → PDF-x
        const Y = (cy) => oy + bh - cy * fy;            // CSS-top-y → PDF-y (flip)
        for (const a of meta.annot) {
          const c = hexRgb(a.color);
          if (a.type === 'edittext') {
            const bgc = hexRgb(a.bg || '#ffffff'); const pad = a.size * 0.3;   // cover ascenders/descenders fully
            pg.drawRectangle({ x: X(a.x - 2), y: Y(a.y + a.h + pad), width: (a.w + 4) * fx, height: (a.h + 2 * pad) * fy, color: rgb(bgc.r, bgc.g, bgc.b) });
            if (a.str) drawTx(pg, a.str, { x: X(a.x), y: Y(a.y + a.size), size: a.size * fy, color: rgb(c.r, c.g, c.b) });
          } else if (a.type === 'text') {
            (a.str || '').split('\n').forEach((ln, j) => drawTx(pg, ln, { x: X(a.x), y: Y(a.y + a.size + j * a.size * 1.2), size: a.size * fy, color: rgb(c.r, c.g, c.b) }));
          } else {
            const opt = { thickness: a.w * fx, color: rgb(c.r, c.g, c.b), opacity: a.type === 'hl' ? 0.32 : 1, lineCap: LineCapStyle.Round };
            for (let j = 1; j < a.pts.length; j++) pg.drawLine({ start: { x: X(a.pts[j - 1].x), y: Y(a.pts[j - 1].y) }, end: { x: X(a.pts[j].x), y: Y(a.pts[j].y) }, ...opt });
          }
        }
      }
      const outB64 = await doc.saveAsBase64();
      const nm = pdfName.replace(/\.pdf$/i, '') + '-edited.pdf';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/pdf', outB64);
      else { const arr = b64ToBytes(outB64); const u = URL.createObjectURL(new Blob([arr], { type: 'application/pdf' })); const a = document.createElement('a'); a.href = u; a.download = nm; a.click(); }
      setStatus('Saved ' + nm + ' — page stays editable text');
    } catch (e) { setStatus('Save failed: ' + e.message); }
    showLoading(false);
  };

  // ---------- PDF pages: organize / rotate / delete / reorder / merge / images / export ----------
  const pagesmodal = document.getElementById('pagesmodal');
  let pgOrder = [], pgRot = {}, pgPendingOp = null;
  function b64ToBytes(b64) { const bin = atob(b64); const u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u; }
  function reopenPdfB64(b64, name) { openPdf(b64ToBytes(b64), name); }
  function openPagesPanel() {
    if (!pdfDoc) { setStatus('Open a PDF first'); return; }
    pgOrder = []; pgRot = {}; for (let i = 0; i < pdfDoc.numPages; i++) pgOrder.push(i);
    renderPagesList(); pagesmodal.classList.add('show');
  }
  function renderPagesList() {
    const el = document.getElementById('pglist'); el.innerHTML = '';
    document.getElementById('pageshead').textContent = 'Pages · ' + pgOrder.length;
    pgOrder.forEach((orig, pos) => {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;gap:8px;padding:8px;border-bottom:1px solid var(--line);color:var(--txt)';
      const rot = pgRot[orig] || 0;
      row.innerHTML = '<span style="flex:1">Page ' + (orig + 1) + (rot ? ' · ' + rot + '°' : '') + '</span>';
      const mk = (t, fn) => { const b = document.createElement('button'); b.className = 'fbtn'; b.textContent = t; b.onclick = fn; return b; };
      row.appendChild(mk('↻', () => { pgRot[orig] = ((pgRot[orig] || 0) + 90) % 360; renderPagesList(); }));
      row.appendChild(mk('↑', () => { if (pos > 0) { [pgOrder[pos - 1], pgOrder[pos]] = [pgOrder[pos], pgOrder[pos - 1]]; renderPagesList(); } }));
      row.appendChild(mk('↓', () => { if (pos < pgOrder.length - 1) { [pgOrder[pos + 1], pgOrder[pos]] = [pgOrder[pos], pgOrder[pos + 1]]; renderPagesList(); } }));
      row.appendChild(mk('🗑', () => { if (pgOrder.length > 1) { pgOrder.splice(pos, 1); renderPagesList(); } else setStatus('At least one page needed'); }));
      el.appendChild(row);
    });
  }
  async function buildEditedPdf() {
    const { PDFDocument, degrees } = PDFLib;
    const src = await PDFDocument.load(pdfBytes.slice(0), loadOpts(pdfPassword));
    const out = await PDFDocument.create();
    const copied = await out.copyPages(src, pgOrder);
    copied.forEach((pg, i) => { const r = pgRot[pgOrder[i]] || 0; if (r) pg.setRotation(degrees((pg.getRotation().angle + r) % 360)); out.addPage(pg); });
    return await out.saveAsBase64();
  }
  document.getElementById('pdfPages').onclick = openPagesPanel;
  document.getElementById('pagesClose').onclick = () => pagesmodal.classList.remove('show');
  pagesmodal.addEventListener('click', (e) => { if (e.target === pagesmodal) pagesmodal.classList.remove('show'); });
  document.getElementById('pgSave').onclick = async () => {
    if (typeof PDFLib === 'undefined' || !pdfBytes) return;
    showLoading(true); setStatus('Saving PDF…');
    try { const out = await buildEditedPdf(); const nm = pdfName.replace(/\.pdf$/i, '') + '-edited.pdf'; savePdfOut(nm, out); pagesmodal.classList.remove('show'); reopenPdfB64(out, nm); setStatus('Saved ' + nm); }
    catch (e) { setStatus('Save failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('pgMerge').onclick = () => { if (AB && AB.pickFor) { pgPendingOp = 'merge'; AB.pickFor('pdf'); } else setStatus('Merge needs the app'); };
  document.getElementById('pgImages').onclick = () => { if (AB && AB.pickFor) { pgPendingOp = 'image'; AB.pickFor('image'); } else setStatus('Needs the app'); };
  window.BrahmPicked = async function (b64, name) {
    const op = pgPendingOp; pgPendingOp = null; if (!op) return;
    showLoading(true);
    try {
      const { PDFDocument } = PDFLib;
      const cur = await PDFDocument.load(pdfBytes.slice(0), loadOpts(pdfPassword));
      if (op === 'merge') {
        setStatus('Merging…');
        const other = await PDFDocument.load(b64ToBytes(b64), loadOpts(""));
        const pages = await cur.copyPages(other, other.getPageIndices()); pages.forEach(p => cur.addPage(p));
        const out = await cur.saveAsBase64(); pagesmodal.classList.remove('show'); reopenPdfB64(out, pdfName.replace(/\.pdf$/i, '') + '-merged.pdf'); setStatus('Merged ' + name);
      } else if (op === 'image') {
        setStatus('Adding image…');
        const bytes = b64ToBytes(b64); const low = (name || '').toLowerCase();
        const img = low.endsWith('.png') ? await cur.embedPng(bytes) : await cur.embedJpg(bytes);
        const pg = cur.addPage([img.width, img.height]); pg.drawImage(img, { x: 0, y: 0, width: img.width, height: img.height });
        const out = await cur.saveAsBase64(); pagesmodal.classList.remove('show'); reopenPdfB64(out, pdfName.replace(/\.pdf$/i, '') + '-img.pdf'); setStatus('Image added');
      }
    } catch (e) { setStatus('Failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('pgExport').onclick = async () => {
    if (!pdfDoc || typeof JSZip === 'undefined') { setStatus('Cannot export'); return; }
    showLoading(true); setStatus('Rendering images…');
    try {
      const zip = new JSZip();
      for (let pos = 0; pos < pgOrder.length; pos++) {
        const page = await pdfDoc.getPage(pgOrder[pos] + 1);
        const vp = page.getViewport({ scale: 2 });
        const cv = document.createElement('canvas'); cv.width = vp.width; cv.height = vp.height;
        await page.render({ canvasContext: cv.getContext('2d'), viewport: vp }).promise;
        const dataB64 = cv.toDataURL('image/png').split(',')[1];
        zip.file('page-' + (pos + 1) + '.png', dataB64, { base64: true });
      }
      const outB64 = await zip.generateAsync({ type: 'base64' });
      const nm = pdfName.replace(/\.pdf$/i, '') + '-images.zip';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/zip', outB64);
      else { const a = document.createElement('a'); a.href = 'data:application/zip;base64,' + outB64; a.download = nm; a.click(); }
      setStatus('Exported ' + pgOrder.length + ' images → ' + nm);
    } catch (e) { setStatus('Export failed: ' + e.message); }
    showLoading(false);
  };
  // ---------- Convert hub (PDF → Word / Text / Images, Images → PDF) ----------
  const convmodal = document.getElementById('convmodal');
  document.getElementById('pdfConvert').onclick = () => { if (pdfDoc) convmodal.classList.add('show'); else setStatus('Open a PDF first'); };
  document.getElementById('convClose').onclick = () => convmodal.classList.remove('show');
  convmodal.addEventListener('click', (e) => { if (e.target === convmodal) convmodal.classList.remove('show'); });
  // Extract text as paragraphs (group by line y, merge lines into paragraphs on big gaps).
  async function pdfTextParagraphs() {
    const pages = [];
    for (let n = 1; n <= pdfDoc.numPages; n++) {
      const page = await pdfDoc.getPage(n); const tc = await page.getTextContent();
      const lines = [];
      for (const it of tc.items) { if (it.str == null) continue; const y = Math.round(it.transform[5]); const x = it.transform[4];
        let ln = lines.find(l => Math.abs(l.y - y) <= 3); if (!ln) { ln = { y, parts: [] }; lines.push(ln); } ln.parts.push({ x, s: it.str }); }
      lines.sort((a, b) => b.y - a.y); lines.forEach(l => l.parts.sort((a, b) => a.x - b.x));
      const paras = []; let cur = '', prevY = null;
      for (const l of lines) { const s = l.parts.map(p => p.s).join('').replace(/\s+/g, ' ').trim(); if (!s) { if (cur) { paras.push(cur); cur = ''; } prevY = null; continue; }
        const gap = prevY == null ? 0 : (prevY - l.y); if (prevY != null && gap > 18) { if (cur) { paras.push(cur); cur = ''; } }
        cur = cur ? (cur + ' ' + s) : s; prevY = l.y; }
      if (cur) paras.push(cur);
      pages.push(paras);
    }
    return pages; // array per page of paragraph strings
  }
  document.getElementById('cv2word').onclick = async () => {
    convmodal.classList.remove('show'); if (!pdfDoc || typeof BrahmDocx === 'undefined' || typeof JSZip === 'undefined') { setStatus('Cannot convert'); return; }
    showLoading(true); setStatus('PDF → Word…');
    try {
      const pages = await pdfTextParagraphs(); const root = document.createElement('div'); let any = false;
      pages.forEach((paras, i) => { paras.forEach(t => { const p = document.createElement('p'); p.textContent = t; root.appendChild(p); any = true; }); if (i < pages.length - 1) root.appendChild(document.createElement('p')); });
      if (!any) { const p = document.createElement('p'); p.textContent = '(No real text found — this PDF looks scanned. Use OCR, then paste into Word.)'; root.appendChild(p); }
      const b64 = await BrahmDocx.build(root, JSZip); const nm = pdfName.replace(/\.pdf$/i, '') + '.docx';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', b64);
      else { const u = b64ToBytes(b64); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([u], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })); a.download = nm; a.click(); }
      setStatus('Saved ' + nm + ' — open it to edit');
    } catch (e) { setStatus('Convert failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('cv2wordOcr').onclick = async () => {
    convmodal.classList.remove('show'); if (!pdfDoc) return;
    if (!(AB && AB.ocr)) { setStatus('OCR convert needs the phone app'); return; }
    if (typeof BrahmDocx === 'undefined' || typeof JSZip === 'undefined') { setStatus('Cannot convert'); return; }
    showLoading(true);
    try {
      const root = document.createElement('div'); let any = false, lastErr = '', pagesDone = 0;
      for (let n = 1; n <= pdfDoc.numPages; n++) {
        setStatus('OCR page ' + n + '/' + pdfDoc.numPages + '…');
        const img = await pageToImgB64(n);
        const res = await ocrImage(img, 'hi', () => setStatus('हिंदी OCR model download ho raha hai… (page ' + n + ')'));
        if (res.err) lastErr = res.err;
        if (res.text && res.text.trim()) {
          pagesDone++;
          res.text.split(/\r?\n/).forEach(line => { const t = line.trim(); if (t) { const p = document.createElement('p'); p.textContent = t; root.appendChild(p); any = true; } });
          if (n < pdfDoc.numPages) root.appendChild(document.createElement('p'));
        }
      }
      if (!any) {
        showLoading(false);
        const dl = /download|model|wait|unavailable|not-ready|timeout/i.test(lastErr);
        setStatus(dl ? 'Hindi OCR model abhi download nahi hua — internet ON karke pehle ek page OCR (🔎) chalao, phir convert karo' : 'OCR ne text nahi padha (err: ' + (lastErr || 'none') + ')');
        return;
      }
      const b64 = await BrahmDocx.build(root, JSZip); const nm = pdfName.replace(/\.pdf$/i, '') + '-ocr.docx';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', b64);
      setStatus('Saved ' + nm + ' · ' + pagesDone + '/' + pdfDoc.numPages + ' pages read — open to edit');
    } catch (e) { setStatus('OCR convert failed: ' + e.message); }
    showLoading(false);
  };
  // Same-look: each PDF page becomes a full-width picture in the Word doc → logo/layout exactly like the PDF.
  document.getElementById('cv2wordLook').onclick = async () => {
    convmodal.classList.remove('show'); if (!pdfDoc) return;
    if (typeof BrahmDocx === 'undefined' || typeof JSZip === 'undefined') { setStatus('Cannot convert'); return; }
    showLoading(true);
    try {
      const root = document.createElement('div');
      for (let n = 1; n <= pdfDoc.numPages; n++) {
        setStatus('Rendering page ' + n + '/' + pdfDoc.numPages + '…');
        const page = await pdfDoc.getPage(n); const base = page.getViewport({ scale: 1 });
        let eff = Math.min(3, 2400 / base.width); const vp = page.getViewport({ scale: eff });
        const cv = document.createElement('canvas'); cv.width = Math.floor(vp.width); cv.height = Math.floor(vp.height);
        const g = cv.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, cv.width, cv.height);
        await page.render({ canvasContext: g, viewport: vp }).promise;
        const img = document.createElement('img');
        img.setAttribute('src', cv.toDataURL('image/jpeg', 0.9));
        img.setAttribute('data-w', cv.width); img.setAttribute('data-h', cv.height);
        root.appendChild(img);
      }
      const b64 = await BrahmDocx.build(root, JSZip); const nm = pdfName.replace(/\.pdf$/i, '') + '-look.docx';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', b64);
      else { const u = b64ToBytes(b64); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([u], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })); a.download = nm; a.click(); }
      setStatus('Saved ' + nm + ' — looks exactly like the PDF');
    } catch (e) { setStatus('Convert failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('cv2text').onclick = async () => {
    convmodal.classList.remove('show'); if (!pdfDoc) return; showLoading(true); setStatus('PDF → Text…');
    try {
      const pages = await pdfTextParagraphs(); const txt = pages.map(ps => ps.join('\n\n')).join('\n\n──────\n\n');
      const b64 = btoa(unescape(encodeURIComponent(txt))); const nm = pdfName.replace(/\.pdf$/i, '') + '.txt';
      if (AB && AB.saveFile) AB.saveFile(nm, 'text/plain', b64);
      else { const a = document.createElement('a'); a.href = 'data:text/plain;charset=utf-8,' + encodeURIComponent(txt); a.download = nm; a.click(); }
      setStatus('Saved ' + nm);
    } catch (e) { setStatus('Convert failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('cv2imgs').onclick = () => { convmodal.classList.remove('show'); if (!pgOrder.length && pdfDoc) { pgOrder = []; for (let i = 0; i < pdfDoc.numPages; i++) pgOrder.push(i); } document.getElementById('pgExport').click(); };
  document.getElementById('cvImg2pdf').onclick = () => { convmodal.classList.remove('show'); document.getElementById('pgImages').click(); };

  async function newBlankPdf() {
    if (typeof PDFLib === 'undefined') { setStatus('Cannot create PDF'); return; }
    try { const { PDFDocument } = PDFLib; const d = await PDFDocument.create(); d.addPage([595, 842]); const b64 = await d.saveAsBase64(); reopenPdfB64(b64, 'new.pdf'); setTimeout(() => { if (currentView === 'pdf') toggleEdit(true); }, 500); }
    catch (e) { setStatus('Failed: ' + e.message); }
  }

  // ---------- OCR (on-device, via Android ML Kit) ----------
  const ocrModal = document.getElementById('ocrmodal');
  let ocrB64 = null, ocrLang = 'hi';
  function centeredPageIdx() {
    const r = pdfview.getBoundingClientRect(); const cy = r.top + r.height / 2; let best = 0, bd = 1e12;
    pageMeta.forEach((m, i) => { const pr = m.pageCanvas.getBoundingClientRect(); const mid = (pr.top + pr.bottom) / 2; const d = Math.abs(mid - cy); if (d < bd) { bd = d; best = i; } });
    return best;
  }
  function runOcr(lang) {
    if (!ocrB64) return;
    ocrLang = lang;
    document.querySelectorAll('.olang').forEach(x => x.classList.toggle('on', x.getAttribute('data-ol') === lang));
    document.getElementById('ocrtext').textContent = 'Reading… (' + (lang === 'hi' ? 'हिंदी' : 'English') + ')';
    if (AB && AB.ocr) AB.ocr(ocrB64, lang);
    else document.getElementById('ocrtext').textContent = 'OCR needs the installed app.';
  }
  document.getElementById('pdfOcr').onclick = () => {
    if (!pdfDoc || !pageMeta.length) { setStatus('Open a PDF first'); return; }
    const m = pageMeta[centeredPageIdx()];
    const cv = m && m.pageCanvas;
    if (!cv) { setStatus('Nothing to read'); return; }
    try { ocrB64 = cv.toDataURL('image/png').split(',')[1]; }
    catch (e) { setStatus('OCR failed to capture page'); return; }
    document.getElementById('ocrhead').textContent = 'OCR · page ' + m.pageNum;
    ocrModal.classList.add('show');
    runOcr('hi');
  };
  let __ocrResolve = null;   // set when a conversion is awaiting an OCR result
  window.BrahmOcrResult = function (b64text, err) {
    let txt = '';
    try { txt = decodeURIComponent(escape(atob(b64text))); } catch (e) { try { txt = atob(b64text); } catch (_) {} }
    if (__ocrResolve) { const r = __ocrResolve; __ocrResolve = null; r({ text: txt, err: err }); return; }
    if (err) { document.getElementById('ocrtext').textContent = 'OCR error: ' + err; return; }
    document.getElementById('ocrtext').textContent = txt.trim() || '(no text found on this page)';
  };
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  function ocrOnce(b64img, lang) {
    return new Promise((resolve) => {
      if (!(AB && AB.ocr)) { resolve({ text: '', err: 'needs app' }); return; }
      __ocrResolve = resolve; try { AB.ocr(b64img, lang); } catch (e) { __ocrResolve = null; resolve({ text: '', err: 'ocr failed' }); }
      setTimeout(() => { if (__ocrResolve === resolve) { __ocrResolve = null; resolve({ text: '', err: 'timeout' }); } }, 40000);
    });
  }
  // Retry while the ML Kit language model is still downloading on first use.
  async function ocrImage(b64img, lang, onWait) {
    for (let attempt = 0; attempt < 6; attempt++) {
      const res = await ocrOnce(b64img, lang);
      if (res.text && res.text.trim()) return res;
      const e = (res.err || '').toLowerCase();
      if (e.includes('download') || e.includes('wait') || e.includes('model') || e.includes('unavailable') || e.includes('timeout')) {
        if (onWait) onWait(attempt); await sleep(3500); continue;
      }
      return res;
    }
    return { text: '', err: 'model-not-ready' };
  }
  // Render a page to a sharp JPEG (smaller than PNG at high res → better OCR, safe over the bridge).
  async function pageToImgB64(n) {
    const page = await pdfDoc.getPage(n);
    const base = page.getViewport({ scale: 1 });
    let eff = Math.min(3.2, 2800 / base.width);          // higher DPI → sharper glyphs → more accurate OCR
    const vp = page.getViewport({ scale: eff });
    const cv = document.createElement('canvas'); cv.width = Math.floor(vp.width); cv.height = Math.floor(vp.height);
    const g = cv.getContext('2d'); g.fillStyle = '#fff'; g.fillRect(0, 0, cv.width, cv.height);
    await page.render({ canvasContext: g, viewport: vp }).promise;
    return cv.toDataURL('image/jpeg', 0.92).split(',')[1];
  }
  document.querySelectorAll('.olang').forEach(b => b.onclick = () => runOcr(b.getAttribute('data-ol')));
  document.getElementById('ocrClose').onclick = () => ocrModal.classList.remove('show');
  ocrModal.addEventListener('click', (e) => { if (e.target === ocrModal) ocrModal.classList.remove('show'); });
  document.getElementById('ocrCopy').onclick = () => {
    const t = document.getElementById('ocrtext');
    try { const rng = document.createRange(); rng.selectNodeContents(t); const sel = getSelection(); sel.removeAllRanges(); sel.addRange(rng); document.execCommand('copy'); sel.removeAllRanges(); setStatus('OCR text copied'); }
    catch (e) { setStatus('Select the text to copy'); }
  };

  // ---------- text / html ----------
  const texttools = document.getElementById('texttools');
  let textName = 'document.txt', textEditable = false, textSnapshot = '';
  function textZoomOn() { currentView = 'text'; textFontSize = 15; textview.style.fontSize = textFontSize + 'px'; zoombar.classList.add('show'); }
  function showText(s) { hideAll(); textZoomOn(); textview.style.display = 'block'; textview.value = s; textview.readOnly = true; textEditable = false; }
  function showEditableText(s, name) {
    hideAll(); textZoomOn(); textview.style.display = 'block'; textview.value = s; textview.readOnly = false; textEditable = true;
    textName = name || 'document.txt'; textSnapshot = s;
    document.getElementById('textname').textContent = textName + '  ·  editable';
    texttools.classList.add('show');
    setStatus('Editing ' + textName + ' · tap Save when done');
  }
  document.getElementById('tUndo').onclick = () => { if (textEditable) textview.value = textSnapshot; };
  document.getElementById('tSave').onclick = () => {
    if (!textEditable) return;
    const text = textview.value; textSnapshot = text;
    let b64; try { b64 = btoa(unescape(encodeURIComponent(text))); } catch (e) { setStatus('Save failed'); return; }
    const low = textName.toLowerCase();
    const mime = low.endsWith('.md') || low.endsWith('.markdown') ? 'text/markdown'
      : low.endsWith('.csv') ? 'text/csv' : low.endsWith('.html') || low.endsWith('.htm') ? 'text/html'
      : low.endsWith('.json') ? 'application/json' : 'text/plain';
    if (AB && AB.saveFile) AB.saveFile(textName, mime, b64);
    else { const a = document.createElement('a'); a.href = 'data:' + mime + ';base64,' + b64; a.download = textName; a.click(); }
    setStatus('Saved ' + textName);
  };
  function showHtml(s) { hideAll(); currentView = 'html'; htmlZoom = 1; htmlview.style.zoom = 1; htmlview.style.display = 'block'; htmlview.srcdoc = s; zoombar.classList.add('show'); setStatus('HTML'); }
  function showMarkdown(s) { showHtml('<pre style="white-space:pre-wrap;font-family:sans-serif;padding:14px;font-size:15px;line-height:1.6">' + s.replace(/[&<>]/g, m => ({ '&':'&amp;','<':'&lt;','>':'&gt;' }[m])) + '</pre>'); }

  const DOC_CSS = '<style>' +
    'html,body{margin:0;background:#525659}' +
    '.page{max-width:820px;margin:14px auto;background:#fff;color:#1a1a1a;padding:48px 56px;box-shadow:0 2px 14px rgba(0,0,0,.4);border-radius:3px;font-family:Calibri,"Segoe UI",Roboto,sans-serif;font-size:16px;line-height:1.55;word-wrap:break-word}' +
    '.page img{max-width:100%;height:auto}' +
    '.page h1{font-size:26px;margin:.3em 0 .5em}.page h2{font-size:21px;margin:.4em 0}.page h3{font-size:18px}' +
    '.page p{margin:.55em 0}.page table{border-collapse:collapse;width:100%;margin:.6em 0}.page td,.page th{border:1px solid #bbb;padding:5px 8px;vertical-align:top}' +
    '.page ul,.page ol{margin:.5em 0 .5em 1.4em}.page a{color:#2B579A}' +
    '@media(max-width:700px){.page{padding:26px 20px;margin:8px auto;font-size:15px}}' +
    '</style><meta name="viewport" content="width=device-width,initial-scale=1">';

  // ---------- Word (.docx): view + EDIT + save as real OOXML ----------
  let wordName = 'document.docx', wordZoom = 1;
  const wordtools = document.getElementById('wordtools');
  function showWord(html, name) {
    hideAll(); currentView = 'word'; wordName = name || 'document.docx'; wordZoom = 1;
    wordview.innerHTML = '<div class="page" contenteditable="true" spellcheck="false">' + (html || '<p>(empty document)</p>') + '</div>';
    wordview.querySelector('.page').style.fontSize = '';
    wordview.style.display = 'block'; wordtools.classList.add('show'); zoombar.classList.add('show');
    document.getElementById('wordname').textContent = name + '  ·  editable';
    setStatus('Word · tap text to edit · Save .docx');
  }
  function openDocx(bytes, name) {
    hideAll(); showLoading(true); setStatus('Opening Word…');
    if (typeof mammoth === 'undefined') { showLoading(false); showText('Word viewer unavailable.'); return; }
    mammoth.convertToHtml({ arrayBuffer: bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) })
      .then((res) => { showWord(res.value || '<p>(empty document)</p>', name); })
      .catch(() => showText('Could not render ' + name))
      .then(() => showLoading(false));
  }
  function wordExec(cmd, val) { try { document.execCommand('styleWithCSS', false, false); document.execCommand(cmd, false, val || null); } catch (e) {} wordview.querySelector('.page') && wordview.querySelector('.page').focus(); }
  document.getElementById('wB').onclick = () => wordExec('bold');
  document.getElementById('wI').onclick = () => wordExec('italic');
  document.getElementById('wU').onclick = () => wordExec('underline');
  document.getElementById('wH1').onclick = () => wordExec('formatBlock', 'H1');
  document.getElementById('wH2').onclick = () => wordExec('formatBlock', 'H2');
  document.getElementById('wP').onclick = () => wordExec('formatBlock', 'P');
  document.getElementById('wUL').onclick = () => wordExec('insertUnorderedList');
  async function buildWordB64() {
    const page = wordview.querySelector('.page');
    if (!page || typeof BrahmDocx === 'undefined' || typeof JSZip === 'undefined') return null;
    return await BrahmDocx.build(page, JSZip);
  }
  document.getElementById('wSave').onclick = async () => {
    showLoading(true); setStatus('Saving .docx…');
    try { const b64 = await buildWordB64(); if (!b64) { setStatus('Save unavailable'); showLoading(false); return; }
      const nm = wordName.replace(/\.(docx?|rtf)$/i, '') + '-edited.docx';
      if (AB && AB.saveFile) AB.saveFile(nm, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', b64);
      else { const u = b64ToBytes(b64); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([u], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })); a.download = nm; a.click(); }
      setStatus('Saved ' + nm);
    } catch (e) { setStatus('Save failed: ' + e.message); }
    showLoading(false);
  };
  document.getElementById('wShare').onclick = async () => {
    try { const b64 = await buildWordB64(); if (!b64) return;
      const nm = wordName.replace(/\.(docx?|rtf)$/i, '') + '.docx';
      if (AB && AB.shareBytes) AB.shareBytes(nm, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', b64);
    } catch (e) { setStatus('Share failed'); }
  };

  async function openPptx(bytes, name) {
    hideAll(); showLoading(true); setStatus('Opening PowerPoint…');
    if (typeof JSZip === 'undefined') { showLoading(false); showText('Viewer unavailable.'); return; }
    try {
      const zip = await JSZip.loadAsync(bytes);
      const slideNames = Object.keys(zip.files).filter(n => /^ppt\/slides\/slide\d+\.xml$/.test(n))
        .sort((a, b) => (+a.match(/(\d+)/)[1]) - (+b.match(/(\d+)/)[1]));
      let body = '';
      for (let i = 0; i < slideNames.length; i++) {
        const xml = await zip.files[slideNames[i]].async('string');
        const paras = xml.split(/<a:p[ >]/).slice(1).map(seg => {
          const runs = [...seg.matchAll(/<a:t>([\s\S]*?)<\/a:t>/g)].map(m => m[1]).join('');
          return runs;
        }).filter(t => t.trim());
        const esc = (t) => t.replace(/[&<>]/g, m => ({ '&':'&amp;','<':'&lt;','>':'&gt;' }[m]));
        let inner = '<div class="snum">Slide ' + (i + 1) + '</div>';
        if (paras.length) { inner += '<h2>' + esc(paras[0]) + '</h2>'; if (paras.length > 1) inner += paras.slice(1).map(p => '<p>' + esc(p) + '</p>').join(''); }
        else inner += '<p style="color:#888">(no text — tap Images above for pictures)</p>';
        body += '<div class="slide">' + inner + '</div>';
      }
      if (!body) body = '<p>No slides found.</p>';
      const css = '<style>html,body{margin:0;background:#525659}.slide{max-width:820px;margin:14px auto;background:#fff;color:#1a1a1a;padding:34px 40px;box-shadow:0 2px 14px rgba(0,0,0,.4);border-radius:3px;font-family:Calibri,"Segoe UI",sans-serif;min-height:380px}.slide h2{font-size:24px;color:#16233F;margin:.1em 0 .5em;border-bottom:3px solid #E8A33D;padding-bottom:8px}.slide p{font-size:17px;line-height:1.5;margin:.5em 0}.snum{font-size:12px;color:#8a93a3;margin-bottom:6px}@media(max-width:700px){.slide{padding:22px 18px;margin:8px auto}}</style><meta name="viewport" content="width=device-width,initial-scale=1">';
      showHtml(css + body); setStatus('PowerPoint · ' + slideNames.length + ' slides · ' + name);
    } catch (e) { showText('Could not render ' + name); }
    showLoading(false);
  }

  // ---------- embedded images ----------
  let images = [];
  const btnImages = document.getElementById('btnImages');
  const gallery = document.getElementById('gallery');
  const lightbox = document.getElementById('lightbox');
  const lightimg = document.getElementById('lightimg');
  function clearImages() { images.forEach(i => { try { URL.revokeObjectURL(i.url); } catch (e) {} }); images = []; btnImages.style.display = 'none'; gallery.classList.remove('show'); lightbox.classList.remove('show'); }
  async function extractImages(bytes) {
    clearImages(); if (typeof JSZip === 'undefined') return;
    try {
      const zip = await JSZip.loadAsync(bytes);
      const names = Object.keys(zip.files).filter(n => /(^|\/)(xl|word|ppt)\/media\//i.test(n) && /\.(png|jpe?g|gif|bmp|webp)$/i.test(n));
      for (const n of names) { const blob = await zip.files[n].async('blob'); images.push({ name: n.split('/').pop(), url: URL.createObjectURL(blob) }); }
      if (images.length) { btnImages.textContent = 'Images (' + images.length + ')'; btnImages.style.display = ''; }
    } catch (e) {}
  }
  function openGallery() {
    if (!images.length) return; gallery.innerHTML = '';
    const head = document.createElement('div'); head.className = 'ghead'; head.innerHTML = '<span>' + images.length + ' image(s)</span>';
    const close = document.createElement('button'); close.className = 'gclose'; close.textContent = 'Close'; close.onclick = () => gallery.classList.remove('show'); head.appendChild(close); gallery.appendChild(head);
    images.forEach(im => { const img = document.createElement('img'); img.src = im.url; img.onclick = () => { lightimg.src = im.url; lightbox.classList.add('show'); }; gallery.appendChild(img); });
    gallery.classList.add('show');
  }
  btnImages.onclick = openGallery;
  lightbox.onclick = () => lightbox.classList.remove('show');

  // ---------- spreadsheet load/save ----------
  function loadWorkbook(bytes, name) {
    const wb = XLSX.read(bytes, { type: 'array' });
    book = [];
    wb.SheetNames.forEach((sn) => { const ws = wb.Sheets[sn]; const s = new E.Sheet(sn); const ref = ws['!ref'];
      if (ref) { const range = XLSX.utils.decode_range(ref);
        for (let R = range.s.r; R <= range.e.r && R < E.MAX_ROWS; R++) for (let C = range.s.c; C <= range.e.c && C < E.MAX_COLS; C++) {
          const cell = ws[XLSX.utils.encode_cell({ r: R, c: C })]; if (!cell) continue;
          let raw; if (cell.f) raw = '=' + cell.f; else if (cell.t === 'n') raw = String(cell.v); else if (cell.t === 'b') raw = cell.v ? 'TRUE' : 'FALSE';
          else raw = cell.w != null ? String(cell.w) : (cell.v != null ? String(cell.v) : '');
          if (raw !== '') s.set(R, C, raw); } }
      book.push(s); });
    if (!book.length) book = [new E.Sheet('Sheet1')];
    active = 0; undoStack.length = 0; renderTabs(); showSheet(); select(0, 0); setStatus('Opened ' + name);
  }
  function loadCsv(text, name) {
    const s = new E.Sheet(name); const sep = name.toLowerCase().endsWith('.tsv') ? '\t' : ',';
    text.split(/\r?\n/).forEach((line, r) => { if (r >= E.MAX_ROWS) return; splitCsv(line, sep).forEach((v, c) => { if (c < E.MAX_COLS && v !== '') s.set(r, c, v); }); });
    book = [s]; active = 0; undoStack.length = 0; renderTabs(); showSheet(); select(0, 0); setStatus('Opened ' + name);
  }
  function splitCsv(line, sep) { const out = []; let sb = '', q = false; for (let i = 0; i < line.length; i++) { const ch = line[i];
    if (ch === '"') { if (q && line[i + 1] === '"') { sb += '"'; i++; } else q = !q; } else if (ch === sep && !q) { out.push(sb); sb = ''; } else sb += ch; } out.push(sb); return out; }
  function sheetToAOA(s) { let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; }
    const aoa = []; for (let r = 0; r <= mr; r++) { const row = []; for (let c = 0; c <= mc; c++) { const v = s.value(r, c); row.push(v.t === 'num' ? v.n : (v.t === 'bool' ? v.b : (v.t === 'blank' ? null : E.asStr(v)))); } aoa.push(row); } return aoa; }
  function saveAs(ext) {
    const wb = XLSX.utils.book_new();
    book.forEach((s) => XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(sheetToAOA(s)), (s.name || 'Sheet').slice(0, 31)));
    const out = XLSX.write(wb, { bookType: ext, type: 'base64' });
    const name = 'BRAHM-Office.' + ext; const mime = ext === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (AB && AB.saveFile) AB.saveFile(name, mime, out);
    else { const bin = atob(out); const arr = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([arr])); a.download = name; a.click(); }
  }

  // ---------- entry ----------
  function setDocTitle(name) { const el = document.getElementById('docTitle'); if (el) el.textContent = name ? ('· ' + name) : ''; }
  window.BrahmOpen = function (b64, name) {
    try {
      const bin = atob(b64); const bytes = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const n = (name || '').toLowerCase();
      setDocTitle(name);
      if (n.endsWith('.pdf')) { clearImages(); openPdf(bytes, name); }
      else if (n.endsWith('.xlsx') || n.endsWith('.xls') || n.endsWith('.xlsm')) { showSheet(); loadWorkbook(bytes, name); extractImages(bytes); }
      else if (n.endsWith('.csv') || n.endsWith('.tsv')) { clearImages(); loadCsv(new TextDecoder().decode(bytes), name); }
      else if (n.endsWith('.html') || n.endsWith('.htm')) { clearImages(); showHtml(new TextDecoder().decode(bytes)); }
      else if (n.endsWith('.md') || n.endsWith('.markdown')) { clearImages(); showEditableText(new TextDecoder().decode(bytes), name); }
      else if (n.endsWith('.docx')) { openDocx(bytes, name); extractImages(bytes); }
      else if (n.endsWith('.pptx')) { openPptx(bytes, name); extractImages(bytes); }
      else if (n.endsWith('.doc') || n.endsWith('.ppt')) { clearImages(); showText('Old-format ' + name + '. Save as .docx/.pptx or PDF to open here.'); }
      else { clearImages(); showEditableText(new TextDecoder().decode(bytes), name || 'document.txt'); }
    } catch (e) { showText('Could not open ' + name + '\n' + e.message); }
  };

  // ---------- buttons ----------
  document.getElementById('btnOpen').onclick = () => { if (AB && AB.pickFile) AB.pickFile(); };
  // ---------- New: blank + templates ----------
  function openBook(sheets, status) { clearImages(); book = sheets; active = 0; undoStack.length = 0; renderTabs(); showSheet(); select(0, 0); setDocTitle(''); setStatus(status); }
  function sheetFromAOA(name, aoa) { const s = new E.Sheet(name); aoa.forEach((row, r) => row.forEach((v, c) => { if (v !== '' && v != null) s.set(r, c, String(v)); })); return s; }
  const TPL_SHEET = {
    invoice: () => [["Brahm InnerBrain Technologies Pvt. Ltd."], ["TAX INVOICE"], ["Invoice No:", "", "Date:", ""], ["Bill To:", ""], [""], ["Item", "Qty", "Rate", "Amount"], ["", "", "", "=B7*C7"], ["", "", "", "=B8*C8"], ["", "", "", "=B9*C9"], [""], ["", "", "Subtotal", "=SUM(D7:D9)"], ["", "", "GST 18%", "=D11*0.18"], ["", "", "Total", "=D11+D12"]],
    budget: () => [["Monthly Budget"], ["Category", "Budget", "Actual", "Difference"], ["Rent", "", "", "=B3-C3"], ["Food", "", "", "=B4-C4"], ["Transport", "", "", "=B5-C5"], ["Utilities", "", "", "=B6-C6"], ["Other", "", "", "=B7-C7"], ["TOTAL", "=SUM(B3:B7)", "=SUM(C3:C7)", "=B8-C8"]],
    opd: () => [["BRAHM Homeopathy — OPD Register"], ["Date", "Patient Name", "Age", "Sex", "Phone", "Complaint", "Medicine", "Fee"], ["", "", "", "", "", "", "", ""], ["", "", "", "", "", "", "", ""], ["", "", "", "", "", "", "", ""], ["", "", "", "", "", "", "Total Fee", "=SUM(H3:H5)"]]
  };
  const TPL_TEXT = {
    letter: { name: 'letter.txt', body: 'Date: \n\nTo,\n[Name]\n[Address]\n\nSubject: \n\nDear Sir/Madam,\n\n\n\n\nRegards,\nSandeep Kushwah\nBrahm InnerBrain Technologies Pvt. Ltd.' },
    meeting: { name: 'meeting-notes.md', body: '# Meeting Notes\n\nDate: \nAttendees: \n\n## Agenda\n- \n\n## Discussion\n- \n\n## Action items\n- [ ] \n\nNext meeting: ' }
  };
  const newmodal = document.getElementById('newmodal');
  function buildNewMenu() {
    const m = document.getElementById('newmenu'); m.innerHTML = '';
    const card = (ic, nm, fn) => { const d = document.createElement('div'); d.className = 'newcard'; d.innerHTML = '<div class="ic">' + ic + '</div><div class="nm">' + nm + '</div>'; d.onclick = () => { newmodal.classList.remove('show'); fn(); }; return d; };
    const sec = (t) => { const d = document.createElement('div'); d.className = 'newsection'; d.textContent = t; return d; };
    m.appendChild(sec('Blank'));
    m.appendChild(card('📊', 'Sheet', () => openBook([new E.Sheet('Sheet1')], 'New sheet')));
    m.appendChild(card('📄', 'PDF', () => newBlankPdf()));
    m.appendChild(card('📝', 'Document', () => showEditableText('', 'document.md')));
    m.appendChild(card('🗒️', 'Note', () => showEditableText('', 'note.txt')));
    m.appendChild(sec('Sheet templates'));
    m.appendChild(card('🧾', 'Invoice', () => openBook([sheetFromAOA('Invoice', TPL_SHEET.invoice())], 'Invoice template')));
    m.appendChild(card('💰', 'Budget', () => openBook([sheetFromAOA('Budget', TPL_SHEET.budget())], 'Budget template')));
    m.appendChild(card('🏥', 'OPD Register', () => openBook([sheetFromAOA('OPD', TPL_SHEET.opd())], 'OPD Register template')));
    m.appendChild(sec('Document templates'));
    m.appendChild(card('✉️', 'Letter', () => showEditableText(TPL_TEXT.letter.body, TPL_TEXT.letter.name)));
    m.appendChild(card('📋', 'Meeting Notes', () => showEditableText(TPL_TEXT.meeting.body, TPL_TEXT.meeting.name)));
  }
  // ---------- immersive full-screen (hide all chrome; more space on small phones) ----------
  const SVG_EXPAND = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3H5a2 2 0 0 0-2 2v3"/><path d="M21 8V5a2 2 0 0 0-2-2h-3"/><path d="M3 16v3a2 2 0 0 0 2 2h3"/><path d="M16 21h3a2 2 0 0 0 2-2v-3"/></svg>';
  const SVG_COLLAPSE = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 9h3a2 2 0 0 0 2-2V4"/><path d="M20 9h-3a2 2 0 0 1-2-2V4"/><path d="M4 15h3a2 2 0 0 1 2 2v3"/><path d="M20 15h-3a2 2 0 0 0-2 2v3"/></svg>';
  const fsBtn = document.getElementById('fsBtn');
  fsBtn.innerHTML = SVG_EXPAND;
  fsBtn.onclick = () => {
    const on = document.body.classList.toggle('immersive');
    fsBtn.innerHTML = on ? SVG_COLLAPSE : SVG_EXPAND;
    if (currentView === 'sheet') { resize(); draw(); }
    if (currentView === 'pdf') scheduleSharpen();
  };

  // ---------- Share the current document ----------
  function bytesToB64(u) { let s = ''; const CH = 0x8000; for (let i = 0; i < u.length; i += CH) s += String.fromCharCode.apply(null, u.subarray(i, i + CH)); return btoa(s); }
  document.getElementById('btnShare').onclick = () => {
    if (!(AB && AB.shareBytes)) { setStatus('Share needs the installed app'); return; }
    try {
      if (currentView === 'pdf' && pdfBytes) { AB.shareBytes(pdfName || 'document.pdf', 'application/pdf', bytesToB64(pdfBytes)); setStatus('Sharing ' + (pdfName || 'PDF')); }
      else if (currentView === 'sheet') {
        const wb = XLSX.utils.book_new(); book.forEach((s) => XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(sheetToAOA(s)), (s.name || 'Sheet').slice(0, 31)));
        const b64 = XLSX.write(wb, { bookType: 'xlsx', type: 'base64' });
        AB.shareBytes('BRAHM-Office.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', b64); setStatus('Sharing sheet');
      }
      else if (currentView === 'text') { const b64 = btoa(unescape(encodeURIComponent(textview.value))); AB.shareBytes(textName || 'document.txt', 'text/plain', b64); setStatus('Sharing ' + (textName || 'text')); }
      else setStatus('Open a document to share');
    } catch (e) { setStatus('Share failed: ' + e.message); }
  };

  document.getElementById('btnNew').onclick = () => { buildNewMenu(); newmodal.classList.add('show'); };
  document.getElementById('newClose').onclick = () => newmodal.classList.remove('show');
  newmodal.addEventListener('click', (e) => { if (e.target === newmodal) newmodal.classList.remove('show'); });
  document.getElementById('btnSaveXlsx').onclick = () => saveAs('xlsx');
  document.getElementById('btnSaveCsv').onclick = () => saveAs('csv');

  // ---------- formatting toolbar + sort ----------
  const fmtbar = document.getElementById('fmtbar');
  document.getElementById('btnFormat').onclick = () => fmtbar.classList.toggle('show');
  document.getElementById('fB').onclick = () => { const cur = (fmtGet(sheet(), selR, selC) || {}).b; fmtSet('b', !cur); updateFmtUI(); };
  document.getElementById('fI').onclick = () => { const cur = (fmtGet(sheet(), selR, selC) || {}).i; fmtSet('i', !cur); updateFmtUI(); };
  document.getElementById('fColor').oninput = (e) => fmtSet('color', e.target.value);
  document.getElementById('fFill').oninput = (e) => fmtSet('fill', e.target.value);
  document.getElementById('fNF').onchange = (e) => fmtSet('nf', e.target.value === 'gen' ? null : e.target.value);
  document.getElementById('fClear').onclick = () => { fmtClear(); updateFmtUI(); };
  document.getElementById('fSortA').onclick = () => sortColumn(true);
  document.getElementById('fSortZ').onclick = () => sortColumn(false);

  // ---------- freeze toggle ----------
  document.getElementById('fFreeze').onclick = () => {
    freezeOn = !freezeOn;
    document.getElementById('fFreeze').classList.toggle('on', freezeOn);
    draw(); setStatus(freezeOn ? 'Freeze on (row 1 & column A pinned)' : 'Freeze off');
  };

  // ---------- copy / paste cell ----------
  let clip = null;
  document.getElementById('fCopy').onclick = () => {
    const s = sheet(); clip = { input: s.raw(selR, selC), fmt: Object.assign({}, fmtGet(s, selR, selC) || {}) };
    setStatus('Copied ' + E.colName(selC) + (selR + 1));
  };
  document.getElementById('fPaste').onclick = () => {
    if (!clip) { setStatus('Nothing copied'); return; }
    snapshot(); const s = sheet();
    s.set(selR, selC, clip.input);
    if (Object.keys(clip.fmt).length) fmtOf(s)[cellKey(selR, selC)] = Object.assign({}, clip.fmt);
    else delete fmtOf(s)[cellKey(selR, selC)];
    select(selR, selC); setStatus('Pasted to ' + E.colName(selC) + (selR + 1));
  };

  // ---------- insert / delete row & column ----------
  function sheetBounds(s) { let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; } return { mr, mc }; }
  function remapCells(s, fn) {
    const oldCells = s.cells, oldFmt = fmtOf(s);
    const nc = new Map(), nf = {};
    for (const [k, v] of oldCells.entries()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; const m = fn(r, c); if (!m) continue; nc.set(cellKey(m.r, m.c), v); }
    for (const kk in oldFmt) { const k = +kk; const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; const m = fn(r, c); if (!m) continue; nf[cellKey(m.r, m.c)] = oldFmt[kk]; }
    s.cells = nc; s.fmt = nf; if (s.cache) s.cache.clear();
  }
  function insertRow(at) { snapshot(); remapCells(sheet(), (r, c) => r >= at ? (r + 1 < E.MAX_ROWS ? { r: r + 1, c } : null) : { r, c }); select(at, selC); setStatus('Row inserted above ' + (at + 1)); }
  function deleteRow(at) { snapshot(); remapCells(sheet(), (r, c) => r === at ? null : (r > at ? { r: r - 1, c } : { r, c })); select(Math.min(at, E.MAX_ROWS - 1), selC); setStatus('Row ' + (at + 1) + ' deleted'); }
  function insertCol(at) { snapshot(); remapCells(sheet(), (r, c) => c >= at ? (c + 1 < E.MAX_COLS ? { r, c: c + 1 } : null) : { r, c }); select(selR, at); setStatus('Column inserted left of ' + E.colName(at)); }
  function deleteCol(at) { snapshot(); remapCells(sheet(), (r, c) => c === at ? null : (c > at ? { r, c: c - 1 } : { r, c })); select(selR, Math.min(at, E.MAX_COLS - 1)); setStatus('Column ' + E.colName(at) + ' deleted'); }
  document.getElementById('fRowIns').onclick = () => insertRow(selR);
  document.getElementById('fRowDel').onclick = () => deleteRow(selR);
  document.getElementById('fColIns').onclick = () => insertCol(selC);
  document.getElementById('fColDel').onclick = () => deleteCol(selC);

  // ---------- charts ----------
  const chartModal = document.getElementById('chartmodal');
  const chartcv = document.getElementById('chartcv');
  const cctx = chartcv.getContext('2d');
  let chartType = 'bar';
  let chartData = null;
  const PAL = ['#1F9D63', '#E8A33D', '#2B579A', '#D64541', '#8e44ad', '#16a085', '#d35400', '#2c3e50'];

  function detectRegion() {
    const s = sheet(); if (!s.cells.size) return null;
    const has = (r, c) => s.raw(r, c) !== '';
    if (!has(selR, selC)) { // fall back to full used range
      let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; }
      return { r0: 0, c0: 0, r1: mr, c1: mc };
    }
    let r0 = selR, r1 = selR, c0 = selC, c1 = selC;
    while (r0 > 0 && (has(r0 - 1, c0) || has(r0 - 1, c1))) r0--;
    while (r1 < E.MAX_ROWS - 1 && (has(r1 + 1, c0) || has(r1 + 1, c1))) r1++;
    while (c0 > 0 && (has(r0, c0 - 1) || has(r1, c0 - 1))) c0--;
    while (c1 < E.MAX_COLS - 1 && (has(r0, c1 + 1) || has(r1, c1 + 1))) c1++;
    return { r0, c0, r1, c1 };
  }
  function buildChartData() {
    const reg = detectRegion(); if (!reg) return null;
    const s = sheet();
    const numAt = (r, c) => { const v = s.value(r, c); return v && v.t === 'num' ? v.n : null; };
    // detect header row: row r0 mostly text
    let headerRow = false, txt = 0, tot = 0;
    for (let c = reg.c0; c <= reg.c1; c++) { tot++; if (numAt(reg.r0, c) === null && s.raw(reg.r0, c) !== '') txt++; }
    if (tot && txt >= tot - 1) headerRow = true;
    const dataR0 = headerRow ? reg.r0 + 1 : reg.r0;
    // label column = first column if its data cells are non-numeric
    let labelCol = -1, lt = 0, ln = 0;
    for (let r = dataR0; r <= reg.r1; r++) { if (numAt(r, reg.c0) === null && s.raw(r, reg.c0) !== '') lt++; if (numAt(r, reg.c0) !== null) ln++; }
    if (lt > ln) labelCol = reg.c0;
    const labels = [];
    for (let r = dataR0; r <= reg.r1; r++) labels.push(labelCol >= 0 ? (s.display(r, reg.c0) || ('Row ' + (r + 1))) : String(r + 1));
    const series = [];
    for (let c = reg.c0; c <= reg.c1; c++) {
      if (c === labelCol) continue;
      const vals = []; let any = false;
      for (let r = dataR0; r <= reg.r1; r++) { const n = numAt(r, c); vals.push(n == null ? 0 : n); if (n != null) any = true; }
      if (!any) continue;
      series.push({ name: headerRow ? (s.display(reg.r0, c) || E.colName(c)) : E.colName(c), data: vals });
    }
    if (!series.length) return null;
    return { labels, series };
  }
  function sizeChartCanvas() {
    const r = chartcv.getBoundingClientRect(); const d = window.devicePixelRatio || 1;
    chartcv.width = Math.floor(r.width * d); chartcv.height = Math.floor((r.height || 360) * d);
    cctx.setTransform(d, 0, 0, d, 0, 0);
    return { w: r.width, h: r.height || 360 };
  }
  function drawChart() {
    if (!chartData) return;
    const { w, h } = sizeChartCanvas();
    cctx.clearRect(0, 0, w, h); cctx.fillStyle = '#fff'; cctx.fillRect(0, 0, w, h);
    const d = chartData;
    if (chartType === 'pie') return drawPie(w, h, d);
    const padL = 46, padB = 46, padT = 16, padR = 12;
    const plotW = w - padL - padR, plotH = h - padT - padB;
    let mn = 0, mx = 0;
    d.series.forEach(s => s.data.forEach(v => { if (v < mn) mn = v; if (v > mx) mx = v; }));
    if (mx === mn) mx = mn + 1;
    const yOf = v => padT + plotH - (v - mn) / (mx - mn) * plotH;
    // axes + gridlines
    cctx.strokeStyle = '#e3e7ee'; cctx.lineWidth = 1; cctx.fillStyle = '#5b6472'; cctx.font = '11px sans-serif'; cctx.textAlign = 'right';
    for (let g = 0; g <= 4; g++) { const val = mn + (mx - mn) * g / 4; const y = yOf(val); cctx.beginPath(); cctx.moveTo(padL, y); cctx.lineTo(w - padR, y); cctx.stroke(); cctx.fillText(fmtAxis(val), padL - 5, y + 3); }
    const n = d.labels.length, groupW = plotW / Math.max(1, n);
    // x labels
    cctx.textAlign = 'center'; cctx.fillStyle = '#16233F';
    const step = Math.ceil(n / Math.max(1, Math.floor(plotW / 48)));
    for (let i = 0; i < n; i++) { if (i % step) continue; const x = padL + groupW * (i + .5); const lab = String(d.labels[i]); cctx.fillText(lab.length > 8 ? lab.slice(0, 7) + '…' : lab, x, h - padB + 16); }
    if (chartType === 'line') {
      d.series.forEach((s, si) => { cctx.strokeStyle = PAL[si % PAL.length]; cctx.lineWidth = 2; cctx.beginPath();
        s.data.forEach((v, i) => { const x = padL + groupW * (i + .5), y = yOf(v); i ? cctx.lineTo(x, y) : cctx.moveTo(x, y); }); cctx.stroke();
        cctx.fillStyle = PAL[si % PAL.length]; s.data.forEach((v, i) => { const x = padL + groupW * (i + .5), y = yOf(v); cctx.beginPath(); cctx.arc(x, y, 3, 0, 7); cctx.fill(); }); });
    } else { // bar
      const bw = groupW / (d.series.length + 0.6);
      d.series.forEach((s, si) => { cctx.fillStyle = PAL[si % PAL.length];
        s.data.forEach((v, i) => { const x = padL + groupW * i + bw * (si + 0.3); const y = yOf(v), y0 = yOf(0); cctx.fillRect(x, Math.min(y, y0), bw * 0.9, Math.abs(y0 - y)); }); });
    }
    drawLegend(w, d.series);
  }
  function drawPie(w, h, d) {
    const s = d.series[0]; const total = s.data.reduce((a, b) => a + Math.max(0, b), 0) || 1;
    const cx = w / 2, cy = h / 2 - 6, R = Math.min(w, h) / 3;
    let a0 = -Math.PI / 2;
    s.data.forEach((v, i) => { const frac = Math.max(0, v) / total; const a1 = a0 + frac * Math.PI * 2;
      cctx.fillStyle = PAL[i % PAL.length]; cctx.beginPath(); cctx.moveTo(cx, cy); cctx.arc(cx, cy, R, a0, a1); cctx.closePath(); cctx.fill();
      if (frac > 0.04) { const mid = (a0 + a1) / 2; cctx.fillStyle = '#fff'; cctx.font = 'bold 11px sans-serif'; cctx.textAlign = 'center'; cctx.fillText(Math.round(frac * 100) + '%', cx + Math.cos(mid) * R * .62, cy + Math.sin(mid) * R * .62 + 3); }
      a0 = a1; });
    cctx.font = '11px sans-serif';
    d.labels.forEach((lab, i) => { const y = 12 + i * 16; if (y > h - 8) return; cctx.fillStyle = PAL[i % PAL.length]; cctx.fillRect(8, y, 11, 11); cctx.fillStyle = '#16233F'; cctx.textAlign = 'left'; cctx.fillText(String(lab).slice(0, 18) + '  (' + s.name + ')', 24, y + 10); });
  }
  function drawLegend(w, series) {
    if (series.length < 2) return; let x = 46;
    cctx.font = '11px sans-serif'; cctx.textAlign = 'left';
    series.forEach((s, si) => { cctx.fillStyle = PAL[si % PAL.length]; cctx.fillRect(x, 2, 11, 11); cctx.fillStyle = '#16233F'; cctx.fillText(s.name, x + 15, 11); x += 30 + cctx.measureText(s.name).width; });
  }
  function fmtAxis(v) { const a = Math.abs(v); if (a >= 1e7) return (v / 1e7).toFixed(1) + 'Cr'; if (a >= 1e5) return (v / 1e5).toFixed(1) + 'L'; if (a >= 1e3) return (v / 1e3).toFixed(1) + 'k'; return (Math.round(v * 100) / 100).toString(); }
  function openChart() {
    chartData = buildChartData();
    if (!chartData) { setStatus('Select a data range with numbers to chart'); return; }
    chartModal.classList.add('show');
    document.getElementById('charttitle').textContent = 'Chart · ' + chartData.series.length + ' series × ' + chartData.labels.length;
    requestAnimationFrame(drawChart);
  }
  document.getElementById('fChart').onclick = openChart;
  document.getElementById('chartClose').onclick = () => chartModal.classList.remove('show');
  chartModal.addEventListener('click', (e) => { if (e.target === chartModal) chartModal.classList.remove('show'); });
  document.querySelectorAll('.ctype').forEach(b => b.onclick = () => {
    document.querySelectorAll('.ctype').forEach(x => x.classList.remove('on')); b.classList.add('on');
    chartType = b.getAttribute('data-ct'); drawChart();
  });

  // ---------- find & replace (across active sheet) ----------
  const frmodal = document.getElementById('frmodal');
  let frPos = { r: -1, c: -1 };
  function frCells() { const s = sheet(); let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; } return { s, mr, mc }; }
  function frFindNext() {
    const q = document.getElementById('frFind').value; if (!q) return;
    const { s, mr, mc } = frCells(); const ql = q.toLowerCase();
    let sr = frPos.r, sc = frPos.c + 1;
    for (let r = Math.max(0, sr); r <= mr; r++) { for (let c = (r === sr ? sc : 0); c <= mc; c++) { if ((s.raw(r, c) || '').toLowerCase().includes(ql)) { frPos = { r, c }; select(r, c); document.getElementById('frStat').textContent = 'Found at ' + E.colName(c) + (r + 1); return true; } } }
    frPos = { r: -1, c: -1 }; document.getElementById('frStat').textContent = 'No more matches'; return false;
  }
  function frReplaceOne() {
    const q = document.getElementById('frFind').value, rep = document.getElementById('frRepl').value; if (!q) return;
    const s = sheet(); const cur = s.raw(selR, selC);
    if (cur && cur.toLowerCase().includes(q.toLowerCase())) { snapshot(); s.set(selR, selC, cur.split(q).join(rep)); frPos = { r: selR, c: selC }; }
    frFindNext();
  }
  function frReplaceAll() {
    const q = document.getElementById('frFind').value, rep = document.getElementById('frRepl').value; if (!q) return;
    const { s, mr, mc } = frCells(); let n = 0; snapshot();
    for (let r = 0; r <= mr; r++) for (let c = 0; c <= mc; c++) { const v = s.raw(r, c); if (v && v.toLowerCase().includes(q.toLowerCase())) { s.set(r, c, v.split(q).join(rep)); n++; } }
    draw(); document.getElementById('frStat').textContent = n + ' replaced';
  }
  document.getElementById('fFR').onclick = () => { frPos = { r: -1, c: -1 }; document.getElementById('frStat').textContent = ''; frmodal.classList.add('show'); };
  document.getElementById('frClose').onclick = () => frmodal.classList.remove('show');
  frmodal.addEventListener('click', (e) => { if (e.target === frmodal) frmodal.classList.remove('show'); });
  document.getElementById('frNext').onclick = frFindNext;
  document.getElementById('frOne').onclick = frReplaceOne;
  document.getElementById('frAll').onclick = frReplaceAll;

  // ---------- data validation (dropdown list) + cell comments ----------
  function dvOf(s) { if (!s.dv) s.dv = {}; return s.dv; }
  function commentsOf(s) { if (!s.comments) s.comments = {}; return s.comments; }
  const dvmodal = document.getElementById('dvmodal');
  document.getElementById('fList').onclick = () => {
    const cur = (dvOf(sheet())[cellKey(selR, selC)] || []).join(', ');
    const inp = prompt('Dropdown options for ' + E.colName(selC) + (selR + 1) + ' (comma separated).\nLeave empty to remove.', cur);
    if (inp === null) return;
    const opts = inp.split(',').map(x => x.trim()).filter(x => x);
    if (opts.length) dvOf(sheet())[cellKey(selR, selC)] = opts; else delete dvOf(sheet())[cellKey(selR, selC)];
    draw(); setStatus(opts.length ? 'Dropdown set' : 'Dropdown removed');
  };
  function showDVChooser(r, c) {
    const opts = (sheet().dv || {})[cellKey(r, c)]; if (!opts) return false;
    const list = document.getElementById('dvlist'); list.innerHTML = '';
    document.getElementById('dvhead').textContent = E.colName(c) + (r + 1) + ' — choose';
    opts.forEach(o => { const d = document.createElement('div'); d.className = 'newcard'; d.innerHTML = '<div class="nm">' + o.replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m])) + '</div>'; d.onclick = () => { snapshot(); sheet().set(r, c, o); dvmodal.classList.remove('show'); select(r, c); }; list.appendChild(d); });
    dvmodal.classList.add('show'); return true;
  }
  document.getElementById('dvClose').onclick = () => dvmodal.classList.remove('show');
  dvmodal.addEventListener('click', (e) => { if (e.target === dvmodal) dvmodal.classList.remove('show'); });
  document.getElementById('fComment').onclick = () => {
    const cm = commentsOf(sheet()); const k = cellKey(selR, selC);
    const cur = cm[k] || '';
    const note = prompt('Note for ' + E.colName(selC) + (selR + 1) + ' (empty to remove):', cur);
    if (note === null) return;
    if (note.trim()) cm[k] = note.trim(); else delete cm[k];
    draw(); setStatus(note.trim() ? 'Note added' : 'Note removed');
  };

  // ---- range select toggle ----
  document.getElementById('fRange').onclick = () => {
    rangeMode = !rangeMode; document.getElementById('fRange').classList.toggle('on', rangeMode);
    setStatus(rangeMode ? 'Range select ON — drag across cells (also drag the green ▪ handle to fill)' : 'Range select off');
  };

  // ---- conditional formatting ----
  const cfmodal = document.getElementById('cfmodal');
  const cfOpEl = document.getElementById('cfOp'), cfValEl = document.getElementById('cfVal'), cfVal2El = document.getElementById('cfVal2');
  const cfBgEl = document.getElementById('cfBg'), cfFgEl = document.getElementById('cfFg'), cfSwatch = document.getElementById('cfSwatch');
  function cfSyncUI() {
    const op = cfOpEl.value; cfVal2El.style.display = (op === 'between') ? '' : 'none';
    cfValEl.style.display = (op === 'notempty') ? 'none' : '';
    cfSwatch.style.background = cfBgEl.value; cfSwatch.style.color = cfFgEl.value;
  }
  cfOpEl.onchange = cfSyncUI; cfBgEl.oninput = cfSyncUI; cfFgEl.oninput = cfSyncUI;
  document.getElementById('fCF').onclick = () => { const b = selBox(); document.getElementById('cfhead').textContent = 'Rule for ' + E.colName(b.c1) + (b.r1 + 1) + ':' + E.colName(b.c2) + (b.r2 + 1); cfSyncUI(); cfmodal.classList.add('show'); };
  document.getElementById('cfClose').onclick = () => cfmodal.classList.remove('show');
  cfmodal.addEventListener('click', (e) => { if (e.target === cfmodal) cfmodal.classList.remove('show'); });
  document.getElementById('cfApply').onclick = () => {
    const s = sheet(); if (!s.cf) s.cf = []; const b = selBox(); const op = cfOpEl.value;
    const rule = { r1: b.r1, c1: b.c1, r2: b.r2, c2: b.c2, op, bg: cfBgEl.value, fg: cfFgEl.value };
    const raw = cfValEl.value.trim(); const n = parseFloat(raw);
    rule.val = isNaN(n) ? null : n; rule.valText = raw; rule.val2 = parseFloat(cfVal2El.value) || 0;
    s.cf.push(rule); cfmodal.classList.remove('show'); draw(); setStatus('Rule applied to ' + ((b.r2 - b.r1 + 1) * (b.c2 - b.c1 + 1)) + ' cells');
  };
  document.getElementById('cfClear').onclick = () => {
    const s = sheet(); if (!s.cf) { cfmodal.classList.remove('show'); return; } const b = selBox();
    s.cf = s.cf.filter(ru => ru.r2 < b.r1 || ru.r1 > b.r2 || ru.c2 < b.c1 || ru.c1 > b.c2);
    cfmodal.classList.remove('show'); draw(); setStatus('Rules cleared on range');
  };

  function sortColumn(asc) {
    const s = sheet();
    let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; }
    if (mr < 1) { setStatus('Nothing to sort'); return; }
    snapshot();
    const rows = []; for (let r = 0; r <= mr; r++) { const row = []; for (let c = 0; c <= mc; c++) row.push(s.raw(r, c)); rows.push(row); }
    const col = selC;
    const key = (row) => { const v = row[col] || ''; const n = parseFloat(v); return { n: (v !== '' && !isNaN(n)) ? n : null, s: v.toLowerCase() }; };
    rows.sort((a, b) => { const A = key(a), B = key(b); let cmp; if (A.n !== null && B.n !== null) cmp = A.n - B.n; else if (A.n !== null) cmp = -1; else if (B.n !== null) cmp = 1; else cmp = A.s < B.s ? -1 : (A.s > B.s ? 1 : 0); return asc ? cmp : -cmp; });
    s.cells.clear(); if (s.cache) s.cache.clear(); s.fmt = {};
    for (let r = 0; r < rows.length; r++) for (let c = 0; c <= mc; c++) { const v = rows[r][c]; if (v !== '') s.set(r, c, v); }
    select(0, col); setStatus('Sorted ' + (asc ? 'A→Z' : 'Z→A') + ' by column ' + E.colName(col));
  }

  applyZoom(); renderTabs(); showSheet(); select(0, 0);
  if (AB && AB.ready) AB.ready();
})();
