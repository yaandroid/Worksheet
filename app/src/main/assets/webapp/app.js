// BRAHM Office — Android WebView UI (PDF via pdf.js, Excel via SheetJS + engine)
// Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
(function () {
  const E = window.BrahmSheet;
  const AB = window.AndroidBridge || null;   // native bridge (may be null in a browser)

  // pdf.js worker
  try { pdfjsLib.GlobalWorkerOptions.workerSrc = 'vendor/pdf.worker.min.js'; } catch (e) {}

  let book = [new E.Sheet('Sheet1')];
  let active = 0;
  const sheet = () => book[active];

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

  let dpr = window.devicePixelRatio || 1;
  let cellW = 92, cellH = 30, rowHead = 46, colHead = 28;
  let scrollX = 0, scrollY = 0, selR = 0, selC = 0;

  // ---------- view switching ----------
  function hideAll() {
    canvas.style.display = 'none'; pdfview.style.display = 'none';
    textview.style.display = 'none'; htmlview.style.display = 'none';
    fbar.style.display = 'none'; zoombar.classList.remove('show');
    pdfview.innerHTML = ''; htmlview.src = 'about:blank';
    const g = document.getElementById('gallery'); if (g) g.classList.remove('show');
    const lb = document.getElementById('lightbox'); if (lb) lb.classList.remove('show');
  }
  function showSheet() { hideAll(); canvas.style.display = 'block'; fbar.style.display = 'flex';
    document.querySelector('.topbar').querySelectorAll('.btn').forEach(b=>{}); resize(); draw(); }
  function showLoading(on) { loading.classList.toggle('show', on); }

  // ---------- grid ----------
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
    const fC = Math.max(0, Math.floor(scrollX / cellW));
    const lC = Math.min(E.MAX_COLS - 1, Math.floor((scrollX + viewW()) / cellW));
    const fR = Math.max(0, Math.floor(scrollY / cellH));
    const lR = Math.min(E.MAX_ROWS - 1, Math.floor((scrollY + viewH()) / cellH));
    ctx.font = '13px sans-serif';
    for (let r = fR; r <= lR; r++) {
      const top = colHead + r * cellH - scrollY;
      for (let c = fC; c <= lC; c++) {
        const left = rowHead + c * cellW - scrollX;
        ctx.fillStyle = (r % 2 === 0) ? '#fff' : '#fafbfc';
        ctx.fillRect(left, top, cellW, cellH);
        if (r === selR && c === selC) { ctx.fillStyle = 'rgba(31,157,99,.18)'; ctx.fillRect(left, top, cellW, cellH); }
        ctx.strokeStyle = '#dce1e8'; ctx.lineWidth = 1; ctx.strokeRect(left + .5, top + .5, cellW, cellH);
        const t = sheet().display(r, c);
        if (t) { ctx.save(); ctx.beginPath(); ctx.rect(left, top, cellW, cellH); ctx.clip();
          const v = sheet().value(r, c);
          ctx.fillStyle = (v && v.t === 'err') ? '#D64541' : '#16233F';
          if (v && v.t === 'num') { ctx.textAlign = 'right'; ctx.fillText(t, left + cellW - 6, top + cellH * .68); }
          else { ctx.textAlign = 'left'; ctx.fillText(t, left + 6, top + cellH * .68); }
          ctx.restore(); }
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

  // touch scroll for grid
  let tStartX = 0, tStartY = 0, tSX = 0, tSY = 0, dragging = false, moved = false;
  canvas.addEventListener('touchstart', (e) => { const t = e.touches[0]; tStartX = t.clientX; tStartY = t.clientY; tSX = scrollX; tSY = scrollY; dragging = true; moved = false; }, { passive: true });
  canvas.addEventListener('touchmove', (e) => { if (!dragging) return; const t = e.touches[0];
    const dx = tStartX - t.clientX, dy = tStartY - t.clientY; if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
    scrollX = clamp(tSX + dx, 0, maxSX()); scrollY = clamp(tSY + dy, 0, maxSY()); draw(); }, { passive: true });
  canvas.addEventListener('touchend', (e) => { dragging = false;
    if (!moved) { const r = canvas.getBoundingClientRect(); const x = (e.changedTouches[0].clientX - r.left); const y = (e.changedTouches[0].clientY - r.top);
      if (x >= rowHead && y >= colHead) { const c = Math.floor((scrollX + x - rowHead) / cellW), row = Math.floor((scrollY + y - colHead) / cellH);
        if (row >= 0 && row < E.MAX_ROWS && c >= 0 && c < E.MAX_COLS) select(row, c); } } }, { passive: true });

  function select(r, c) { commit(); selR = r; selC = c; cellref.textContent = E.colName(c) + (r + 1); finput.value = sheet().raw(r, c); ensureVis(); draw(); }
  function ensureVis() { const cl = selC * cellW, ct = selR * cellH;
    if (cl < scrollX) scrollX = cl; else if (cl + cellW > scrollX + viewW()) scrollX = cl + cellW - viewW();
    if (ct < scrollY) scrollY = ct; else if (ct + cellH > scrollY + viewH()) scrollY = ct + cellH - viewH();
    scrollX = clamp(scrollX, 0, maxSX()); scrollY = clamp(scrollY, 0, maxSY()); }
  function commit() { const raw = finput.value; if (raw !== sheet().raw(selR, selC)) sheet().set(selR, selC, raw); }
  finput.addEventListener('keydown', (e) => { if (e.key === 'Enter') { commit(); select(Math.min(E.MAX_ROWS - 1, selR + 1), selC); e.preventDefault(); } });
  finput.addEventListener('blur', commit);

  function renderTabs() { const t = document.getElementById('tabs'); t.innerHTML = '';
    book.forEach((s, i) => { const d = document.createElement('div'); d.className = 'sheettab' + (i === active ? ' active' : ''); d.textContent = s.name;
      d.onclick = () => { active = i; renderTabs(); select(0, 0); }; t.appendChild(d); });
    const add = document.createElement('div'); add.className = 'sheettab'; add.textContent = '+';
    add.onclick = () => { book.push(new E.Sheet('Sheet' + (book.length + 1))); active = book.length - 1; renderTabs(); select(0, 0); }; t.appendChild(add); }

  // ---------- PDF (pdf.js) ----------
  let pdfDoc = null, pdfScale = 1.1;
  async function openPdf(bytes) {
    hideAll(); pdfview.style.display = 'block'; zoombar.classList.add('show'); showLoading(true);
    try {
      pdfDoc = await pdfjsLib.getDocument({ data: bytes }).promise;
      await renderPdf();
      setStatus('PDF · ' + pdfDoc.numPages + ' pages');
    } catch (e) { setStatus('PDF error: ' + e.message); textFallback('Could not open this PDF.'); }
    showLoading(false);
  }
  async function renderPdf() {
    pdfview.innerHTML = '';
    const width = pdfview.clientWidth || window.innerWidth;
    for (let n = 1; n <= pdfDoc.numPages; n++) {
      const page = await pdfDoc.getPage(n);
      const base = page.getViewport({ scale: 1 });
      const scale = (width - 20) / base.width * pdfScale;
      const vp = page.getViewport({ scale });
      const cv = document.createElement('canvas');
      const c2 = cv.getContext('2d');
      const outScale = Math.min(window.devicePixelRatio || 1, 2);
      cv.width = Math.floor(vp.width * outScale); cv.height = Math.floor(vp.height * outScale);
      cv.style.width = Math.floor(vp.width) + 'px'; cv.style.height = Math.floor(vp.height) + 'px';
      pdfview.appendChild(cv);
      await page.render({ canvasContext: c2, viewport: vp, transform: outScale !== 1 ? [outScale, 0, 0, outScale, 0, 0] : null }).promise;
    }
  }
  document.getElementById('zoomIn').onclick = async () => { if (!pdfDoc) return; pdfScale = Math.min(3, pdfScale + 0.25); showLoading(true); await renderPdf(); showLoading(false); };
  document.getElementById('zoomOut').onclick = async () => { if (!pdfDoc) return; pdfScale = Math.max(0.5, pdfScale - 0.25); showLoading(true); await renderPdf(); showLoading(false); };

  // ---------- text / html ----------
  function textFallback(s) { hideAll(); textview.style.display = 'block'; textview.textContent = s; }
  function showText(s) { hideAll(); textview.style.display = 'block'; textview.textContent = s; setStatus('Text file'); }
  function showHtml(s) { hideAll(); htmlview.style.display = 'block'; htmlview.srcdoc = s; setStatus('HTML'); }
  function showMarkdown(s) { showHtml('<pre style="white-space:pre-wrap;font-family:sans-serif;padding:14px;font-size:15px;line-height:1.6">' + s.replace(/[&<>]/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[m])) + '</pre>'); }

  function setStatus(s) { document.getElementById('status').textContent = s + ' · Developed by Sandeep Kushwah'; }

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
    active = 0; renderTabs(); showSheet(); select(0, 0); setStatus('Opened ' + name);
  }
  function loadCsv(text, name) {
    const s = new E.Sheet(name); const sep = name.toLowerCase().endsWith('.tsv') ? '\t' : ',';
    text.split(/\r?\n/).forEach((line, r) => { if (r >= E.MAX_ROWS) return; splitCsv(line, sep).forEach((v, c) => { if (c < E.MAX_COLS && v !== '') s.set(r, c, v); }); });
    book = [s]; active = 0; renderTabs(); showSheet(); select(0, 0); setStatus('Opened ' + name);
  }
  function splitCsv(line, sep) { const out = []; let sb = '', q = false; for (let i = 0; i < line.length; i++) { const ch = line[i];
    if (ch === '"') { if (q && line[i + 1] === '"') { sb += '"'; i++; } else q = !q; } else if (ch === sep && !q) { out.push(sb); sb = ''; } else sb += ch; } out.push(sb); return out; }

  function sheetToAOA(s) { let mr = 0, mc = 0; for (const k of s.cells.keys()) { const r = Math.floor(k / E.MAX_COLS), c = k % E.MAX_COLS; if (r > mr) mr = r; if (c > mc) mc = c; }
    const aoa = []; for (let r = 0; r <= mr; r++) { const row = []; for (let c = 0; c <= mc; c++) { const v = s.value(r, c);
      row.push(v.t === 'num' ? v.n : (v.t === 'bool' ? v.b : (v.t === 'blank' ? null : E.asStr(v)))); } aoa.push(row); } return aoa; }

  function saveAs(ext) {
    const wb = XLSX.utils.book_new();
    book.forEach((s) => XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(sheetToAOA(s)), (s.name || 'Sheet').slice(0, 31)));
    const out = XLSX.write(wb, { bookType: ext, type: 'base64' });
    const name = 'BRAHM-Office.' + ext;
    const mime = ext === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (AB && AB.saveFile) AB.saveFile(name, mime, out);
    else { const bin = atob(out); const arr = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([arr])); a.download = name; a.click(); }
  }

  // ---------- embedded images (xlsx/docx are ZIPs) ----------
  let images = [];
  const btnImages = document.getElementById('btnImages');
  const gallery = document.getElementById('gallery');
  const lightbox = document.getElementById('lightbox');
  const lightimg = document.getElementById('lightimg');

  function clearImages() {
    images.forEach(i => { try { URL.revokeObjectURL(i.url); } catch (e) {} });
    images = []; btnImages.style.display = 'none';
    gallery.classList.remove('show'); lightbox.classList.remove('show');
  }

  async function extractImages(bytes) {
    clearImages();
    if (typeof JSZip === 'undefined') return;
    try {
      const zip = await JSZip.loadAsync(bytes);
      const names = Object.keys(zip.files).filter(n =>
        /(^|\/)(xl|word|ppt)\/media\//i.test(n) && /\.(png|jpe?g|gif|bmp|webp|emf|wmf)$/i.test(n));
      for (const n of names) {
        const ext = n.split('.').pop().toLowerCase();
        if (ext === 'emf' || ext === 'wmf') continue; // not browser-renderable
        const blob = await zip.files[n].async('blob');
        images.push({ name: n.split('/').pop(), url: URL.createObjectURL(blob) });
      }
      if (images.length) { btnImages.textContent = 'Images (' + images.length + ')'; btnImages.style.display = ''; }
    } catch (e) {}
  }

  function openGallery() {
    if (!images.length) return;
    gallery.innerHTML = '';
    const head = document.createElement('div'); head.className = 'ghead';
    head.innerHTML = '<span>' + images.length + ' image(s) in this file</span>';
    const close = document.createElement('button'); close.className = 'gclose'; close.textContent = 'Close';
    close.onclick = () => gallery.classList.remove('show'); head.appendChild(close); gallery.appendChild(head);
    images.forEach(im => { const img = document.createElement('img'); img.src = im.url; img.alt = im.name;
      img.onclick = () => { lightimg.src = im.url; lightbox.classList.add('show'); }; gallery.appendChild(img); });
    gallery.classList.add('show');
  }
  btnImages.onclick = openGallery;
  lightbox.onclick = () => lightbox.classList.remove('show');

  // ---------- entry point called by Android ----------
  // base64 bytes + filename. Routes by extension.
  window.BrahmOpen = function (b64, name) {
    try {
      const bin = atob(b64); const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const n = (name || '').toLowerCase();
      if (n.endsWith('.pdf')) { clearImages(); openPdf(bytes); }
      else if (n.endsWith('.xlsx') || n.endsWith('.xls') || n.endsWith('.xlsm')) { showSheet(); loadWorkbook(bytes, name); extractImages(bytes); }
      else if (n.endsWith('.csv') || n.endsWith('.tsv')) { clearImages(); loadCsv(new TextDecoder().decode(bytes), name); }
      else if (n.endsWith('.html') || n.endsWith('.htm')) { clearImages(); showHtml(new TextDecoder().decode(bytes)); }
      else if (n.endsWith('.md') || n.endsWith('.markdown')) { clearImages(); showMarkdown(new TextDecoder().decode(bytes)); }
      else if (n.endsWith('.docx') || n.endsWith('.pptx')) {
        // Open the images now; full Word/PPT layout view comes next.
        showText('Opened ' + name + '.\n\nTap “Images” above to see pictures inside this file.\nFull Word/PowerPoint page view is coming next.');
        extractImages(bytes);
      }
      else if (n.endsWith('.doc') || n.endsWith('.ppt')) {
        clearImages();
        showText('This is an old-format ' + name + ' file.\n\nFull view is coming next — meanwhile save it as .docx/.pptx or PDF to open it here.');
      }
      else { clearImages(); showText(new TextDecoder().decode(bytes)); }
    } catch (e) { textFallback('Could not open ' + name + '\n' + e.message); }
  };

  // ---------- buttons ----------
  document.getElementById('btnOpen').onclick = () => { if (AB && AB.pickFile) AB.pickFile(); };
  document.getElementById('btnNew').onclick = () => { clearImages(); book = [new E.Sheet('Sheet1')]; active = 0; renderTabs(); showSheet(); select(0, 0); setStatus('New sheet'); };
  document.getElementById('btnSaveXlsx').onclick = () => saveAs('xlsx');
  document.getElementById('btnSaveCsv').onclick = () => saveAs('csv');

  // init
  renderTabs(); showSheet(); select(0, 0);
  if (AB && AB.ready) AB.ready();
})();
