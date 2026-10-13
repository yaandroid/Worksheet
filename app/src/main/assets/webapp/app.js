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
  let scrollX = 0, scrollY = 0, selR = 0, selC = 0;

  // ---------- view switching ----------
  function hideAll() {
    canvas.style.display = 'none'; pdfview.style.display = 'none';
    textview.style.display = 'none'; htmlview.style.display = 'none';
    fbar.style.display = 'none'; zoombar.classList.remove('show'); aclist.classList.remove('show');
    const fb = document.getElementById('fmtbar'); if (fb) fb.classList.remove('show');
    pdfview.innerHTML = ''; htmlview.src = 'about:blank';
    document.getElementById('gallery').classList.remove('show');
    document.getElementById('lightbox').classList.remove('show');
    hideLoupe();
  }
  function showSheet() { hideAll(); canvas.style.display = 'block'; fbar.style.display = 'flex'; resize(); draw(); }
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

  function draw() {
    if (canvas.style.display === 'none') return;
    const w = canvas.width / dpr, h = canvas.height / dpr;
    ctx.clearRect(0, 0, w, h);
    const fs = Math.max(11, Math.round(13 * gridZoom));
    ctx.font = fs + 'px sans-serif';
    const fC = Math.max(0, Math.floor(scrollX / cellW));
    const lC = Math.min(E.MAX_COLS - 1, Math.floor((scrollX + viewW()) / cellW));
    const fR = Math.max(0, Math.floor(scrollY / cellH));
    const lR = Math.min(E.MAX_ROWS - 1, Math.floor((scrollY + viewH()) / cellH));
    for (let r = fR; r <= lR; r++) {
      const top = colHead + r * cellH - scrollY;
      for (let c = fC; c <= lC; c++) {
        const left = rowHead + c * cellW - scrollX;
        const f = fmtGet(sheet(), r, c);
        ctx.fillStyle = (f && f.fill) ? f.fill : ((r % 2 === 0) ? '#fff' : '#fafbfc');
        ctx.fillRect(left, top, cellW, cellH);
        if (r === selR && c === selC) { ctx.fillStyle = 'rgba(31,157,99,.18)'; ctx.fillRect(left, top, cellW, cellH); }
        ctx.strokeStyle = '#dce1e8'; ctx.lineWidth = 1; ctx.strokeRect(left + .5, top + .5, cellW, cellH);
        const v = sheet().value(r, c);
        const isNum = v && v.t === 'num';
        let t = (f && f.nf && isNum) ? formatNum(v.n, f.nf) : sheet().display(r, c);
        if (t) { ctx.save(); ctx.beginPath(); ctx.rect(left, top, cellW, cellH); ctx.clip();
          ctx.font = ((f && f.i) ? 'italic ' : '') + ((f && f.b) ? 'bold ' : '') + fs + 'px sans-serif';
          ctx.fillStyle = (v && v.t === 'err') ? '#D64541' : ((f && f.color) ? f.color : '#16233F');
          if (isNum) { ctx.textAlign = 'right'; ctx.fillText(t, left + cellW - 6, top + cellH * .68); }
          else { ctx.textAlign = 'left'; ctx.fillText(t, left + 6, top + cellH * .68); }
          ctx.restore(); ctx.font = fs + 'px sans-serif'; }
      }
    }
    if (selR >= fR && selR <= lR && selC >= fC && selC <= lC) {
      const l = rowHead + selC * cellW - scrollX, t = colHead + selR * cellH - scrollY;
      ctx.strokeStyle = '#1F9D63'; ctx.lineWidth = 2; ctx.strokeRect(l + 1, t + 1, cellW - 2, cellH - 2);
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
  }

  // ---------- grid gestures: drag-scroll, tap-select, double-tap zoom, long-press magnify ----------
  let gStartX = 0, gStartY = 0, gSX = 0, gSY = 0, gMoved = false, gScroll = false, gLong = null, gMag = false;
  let gLastTap = 0, gLastX = 0, gLastY = 0;

  canvas.addEventListener('touchstart', (e) => {
    const t = e.touches[0];
    gStartX = t.clientX; gStartY = t.clientY; gSX = scrollX; gSY = scrollY;
    gMoved = false; gScroll = false; gMag = false;
    gLong = setTimeout(() => { gMag = true; showLoupeAt(canvas, t.clientX, t.clientY); }, 380);
  }, { passive: true });

  canvas.addEventListener('touchmove', (e) => {
    const t = e.touches[0];
    const dx = gStartX - t.clientX, dy = gStartY - t.clientY;
    if (gMag) { showLoupeAt(canvas, t.clientX, t.clientY); if (e.cancelable) e.preventDefault(); return; }
    if (Math.abs(dx) + Math.abs(dy) > 8) { gMoved = true; gScroll = true; clearTimeout(gLong);
      scrollX = clamp(gSX + dx, 0, maxSX()); scrollY = clamp(gSY + dy, 0, maxSY()); draw(); }
  }, { passive: false });

  canvas.addEventListener('touchend', (e) => {
    clearTimeout(gLong);
    if (gMag) { gMag = false; hideLoupe(); return; }
    if (gScroll || gMoved) return;
    const r = canvas.getBoundingClientRect();
    const x = gStartX - r.left, y = gStartY - r.top;
    const now = Date.now();
    if (now - gLastTap < 300 && Math.abs(x - gLastX) < 30 && Math.abs(y - gLastY) < 30) {
      gLastTap = 0; cycleGridZoom(); return;    // double-tap = zoom
    }
    gLastTap = now; gLastX = x; gLastY = y;
    if (x >= rowHead && y >= colHead) {
      const c = Math.floor((scrollX + x - rowHead) / cellW), row = Math.floor((scrollY + y - colHead) / cellH);
      if (row >= 0 && row < E.MAX_ROWS && c >= 0 && c < E.MAX_COLS) select(row, c);
    }
  }, { passive: true });

  function cycleGridZoom() {
    gridZoom = gridZoom >= 1.9 ? 1 : (gridZoom >= 1.4 ? 2 : 1.5);
    applyZoom(); scrollX = clamp(scrollX, 0, maxSX()); scrollY = clamp(scrollY, 0, maxSY()); draw();
  }

  function select(r, c) { commit(); selR = r; selC = c; cellref.textContent = E.colName(c) + (r + 1); finput.value = sheet().raw(r, c); aclist.classList.remove('show'); updateFmtUI(); ensureVis(); draw(); }
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
  let pdfDoc = null, pdfScale = 1.1;
  async function openPdf(bytes) {
    hideAll(); pdfview.style.display = 'block'; zoombar.classList.add('show'); showLoading(true);
    try { pdfDoc = await pdfjsLib.getDocument({ data: bytes }).promise; await renderPdf(); setStatus('PDF · ' + pdfDoc.numPages + ' pages · double-tap to zoom'); }
    catch (e) { setStatus('PDF error'); showText('Could not open this PDF.'); }
    showLoading(false);
  }
  async function renderPdf() {
    pdfview.innerHTML = '';
    const width = pdfview.clientWidth || window.innerWidth;
    for (let n = 1; n <= pdfDoc.numPages; n++) {
      const page = await pdfDoc.getPage(n);
      const base = page.getViewport({ scale: 1 });
      const scale = (width - 16) / base.width * pdfScale;
      const vp = page.getViewport({ scale });
      const cv = document.createElement('canvas'); const c2 = cv.getContext('2d');
      const os = Math.min(window.devicePixelRatio || 1, 2);
      cv.width = Math.floor(vp.width * os); cv.height = Math.floor(vp.height * os);
      cv.style.width = Math.floor(vp.width) + 'px'; cv.style.height = Math.floor(vp.height) + 'px';
      pdfview.appendChild(cv);
      await page.render({ canvasContext: c2, viewport: vp, transform: os !== 1 ? [os, 0, 0, os, 0, 0] : null }).promise;
    }
  }
  async function setPdfScale(s) { pdfScale = clamp(s, 0.5, 4); showLoading(true); await renderPdf(); showLoading(false); }
  document.getElementById('zoomIn').onclick = () => { if (pdfDoc) setPdfScale(pdfScale + 0.25); };
  document.getElementById('zoomOut').onclick = () => { if (pdfDoc) setPdfScale(pdfScale - 0.25); };

  // PDF gestures: double-tap zoom + long-press magnify
  let pLong = null, pMag = false, pLastTap = 0, pLX = 0, pLY = 0, pStartX = 0, pStartY = 0;
  pdfview.addEventListener('touchstart', (e) => {
    if (e.touches.length > 1) return;
    const t = e.touches[0]; pStartX = t.clientX; pStartY = t.clientY; pMag = false;
    pLong = setTimeout(() => { pMag = true; const el = document.elementFromPoint(t.clientX, t.clientY); if (el && el.tagName === 'CANVAS') showLoupeAt(el, t.clientX, t.clientY); }, 420);
  }, { passive: true });
  pdfview.addEventListener('touchmove', (e) => {
    const t = e.touches[0];
    if (pMag) { const el = document.elementFromPoint(t.clientX, t.clientY); if (el && el.tagName === 'CANVAS') showLoupeAt(el, t.clientX, t.clientY); if (e.cancelable) e.preventDefault(); return; }
    if (Math.abs(pStartX - t.clientX) + Math.abs(pStartY - t.clientY) > 10) clearTimeout(pLong);
  }, { passive: false });
  pdfview.addEventListener('touchend', (e) => {
    clearTimeout(pLong);
    if (pMag) { pMag = false; hideLoupe(); return; }
    const now = Date.now();
    if (now - pLastTap < 300 && Math.abs(pStartX - pLX) < 40 && Math.abs(pStartY - pLY) < 40) {
      pLastTap = 0; setPdfScale(pdfScale > 1.5 ? 1.1 : 2.4);   // double-tap zoom toggle
    } else { pLastTap = now; pLX = pStartX; pLY = pStartY; }
  }, { passive: true });

  // ---------- text / html ----------
  function showText(s) { hideAll(); textview.style.display = 'block'; textview.textContent = s; }
  function showHtml(s) { hideAll(); htmlview.style.display = 'block'; htmlview.srcdoc = s; setStatus('HTML'); }
  function showMarkdown(s) { showHtml('<pre style="white-space:pre-wrap;font-family:sans-serif;padding:14px;font-size:15px;line-height:1.6">' + s.replace(/[&<>]/g, m => ({ '&':'&amp;','<':'&lt;','>':'&gt;' }[m])) + '</pre>'); }

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
  window.BrahmOpen = function (b64, name) {
    try {
      const bin = atob(b64); const bytes = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const n = (name || '').toLowerCase();
      if (n.endsWith('.pdf')) { clearImages(); openPdf(bytes); }
      else if (n.endsWith('.xlsx') || n.endsWith('.xls') || n.endsWith('.xlsm')) { showSheet(); loadWorkbook(bytes, name); extractImages(bytes); }
      else if (n.endsWith('.csv') || n.endsWith('.tsv')) { clearImages(); loadCsv(new TextDecoder().decode(bytes), name); }
      else if (n.endsWith('.html') || n.endsWith('.htm')) { clearImages(); showHtml(new TextDecoder().decode(bytes)); }
      else if (n.endsWith('.md') || n.endsWith('.markdown')) { clearImages(); showMarkdown(new TextDecoder().decode(bytes)); }
      else if (n.endsWith('.docx') || n.endsWith('.pptx')) { showText('Opened ' + name + '.\n\nTap “Images” above to see pictures inside this file.\nFull Word/PowerPoint page view is coming next.'); extractImages(bytes); }
      else if (n.endsWith('.doc') || n.endsWith('.ppt')) { clearImages(); showText('Old-format ' + name + '. Save as .docx/.pptx or PDF to open here.'); }
      else { clearImages(); showText(new TextDecoder().decode(bytes)); }
    } catch (e) { showText('Could not open ' + name + '\n' + e.message); }
  };

  // ---------- buttons ----------
  document.getElementById('btnOpen').onclick = () => { if (AB && AB.pickFile) AB.pickFile(); };
  document.getElementById('btnNew').onclick = () => { clearImages(); book = [new E.Sheet('Sheet1')]; active = 0; undoStack.length = 0; renderTabs(); showSheet(); select(0, 0); setStatus('New sheet'); };
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
