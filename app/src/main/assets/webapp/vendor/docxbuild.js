/* BRAHM Office — minimal but REAL OOXML .docx writer (round-trips in Word/WPS/mammoth).
   Walks an edited DOM subtree and emits word/document.xml with proper paragraphs, runs
   (bold/italic/underline), headings, bullet/number lists and basic tables. No altChunk. */
(function (global) {
  function esc(s) { return String(s).replace(/[&<>]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[m])); }
  const BLOCK = { P: 1, DIV: 1, H1: 1, H2: 1, H3: 1, H4: 1, H5: 1, H6: 1, LI: 1, BLOCKQUOTE: 1, PRE: 1 };
  const HSTYLE = { H1: 'Heading1', H2: 'Heading2', H3: 'Heading3', H4: 'Heading4', H5: 'Heading5', H6: 'Heading6' };

  function runsFrom(node, st, out) {
    for (let c = node.firstChild; c; c = c.nextSibling) {
      if (c.nodeType === 3) {                       // text
        const t = c.nodeValue; if (t === '') continue;
        const rpr = (st.b ? '<w:b/>' : '') + (st.i ? '<w:i/>' : '') + (st.u ? '<w:u w:val="single"/>' : '');
        out.push('<w:r>' + (rpr ? '<w:rPr>' + rpr + '</w:rPr>' : '') + '<w:t xml:space="preserve">' + esc(t) + '</w:t></w:r>');
        continue;
      }
      if (c.nodeType !== 1) continue;
      const tag = c.tagName.toUpperCase();
      if (tag === 'BR') { out.push('<w:r><w:br/></w:r>'); continue; }
      const ns = { b: st.b, i: st.i, u: st.u };
      if (tag === 'B' || tag === 'STRONG') ns.b = true;
      if (tag === 'I' || tag === 'EM') ns.i = true;
      if (tag === 'U') ns.u = true;
      runsFrom(c, ns, out);
    }
  }

  function para(el, styleOverride) {
    const out = []; runsFrom(el, { b: false, i: false, u: false }, out);
    const tag = el.tagName ? el.tagName.toUpperCase() : 'P';
    const style = styleOverride || HSTYLE[tag];
    const ppr = style ? '<w:pPr><w:pStyle w:val="' + style + '"/></w:pPr>' : '';
    return '<w:p>' + ppr + out.join('') + '</w:p>';
  }
  // list item: paragraph tied to numbering (0 = bullet, 1 = decimal)
  function listPara(el, numId) {
    const out = []; runsFrom(el, { b: false, i: false, u: false }, out);
    return '<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="' + numId + '"/></w:numPr></w:pPr>' + out.join('') + '</w:p>';
  }
  function tableXml(tbl) {
    let x = '<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/>' +
      '<w:tblBorders><w:top w:val="single" w:sz="4" w:color="999999"/><w:left w:val="single" w:sz="4" w:color="999999"/><w:bottom w:val="single" w:sz="4" w:color="999999"/><w:right w:val="single" w:sz="4" w:color="999999"/><w:insideH w:val="single" w:sz="4" w:color="999999"/><w:insideV w:val="single" w:sz="4" w:color="999999"/></w:tblBorders></w:tblPr>';
    const rows = tbl.querySelectorAll ? tbl.querySelectorAll('tr') : [];
    rows.forEach(tr => {
      x += '<w:tr>';
      const cells = tr.querySelectorAll('td,th');
      cells.forEach(td => {
        const out = []; runsFrom(td, { b: td.tagName.toUpperCase() === 'TH', i: false, u: false }, out);
        x += '<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/></w:tcPr><w:p>' + out.join('') + '</w:p></w:tc>';
      });
      x += '</w:tr>';
    });
    return x + '</w:tbl>';
  }

  // ---- images (e.g. a whole PDF page rendered as a picture so the Word looks exactly like the PDF) ----
  let IMGS = [];
  function imgPara(img) {
    const src = img.getAttribute('src') || '';
    const m = /^data:image\/(png|jpe?g);base64,(.+)$/i.exec(src); if (!m) return '<w:p/>';
    const ext = /png/i.test(m[1]) ? 'png' : 'jpeg';
    const id = IMGS.length + 1, rId = 'rIdImg' + id;
    const w = parseInt(img.getAttribute('data-w')) || img.naturalWidth || 800;
    const h = parseInt(img.getAttribute('data-h')) || img.naturalHeight || 1100;
    const cx = 5734050, cy = Math.max(1, Math.round(cx * (h / w)));   // full content width (~6.27in), keep aspect
    IMGS.push({ rId: rId, data: m[2], ext: ext, id: id });
    return '<w:p><w:pPr><w:jc w:val="center"/><w:spacing w:after="0"/></w:pPr><w:r><w:drawing>' +
      '<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="' + cx + '" cy="' + cy + '"/><wp:docPr id="' + id + '" name="Page' + id + '"/>' +
      '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic>' +
      '<pic:nvPicPr><pic:cNvPr id="' + id + '" name="Page' + id + '"/><pic:cNvPicPr/></pic:nvPicPr>' +
      '<pic:blipFill><a:blip r:embed="' + rId + '"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>' +
      '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="' + cx + '" cy="' + cy + '"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>' +
      '</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>';
  }
  // Build the <w:body> inner xml from a root element's block children.
  function bodyXml(root) {
    const parts = [];
    function walk(node) {
      for (let c = node.firstChild; c; c = c.nextSibling) {
        if (c.nodeType !== 1) continue;             // skip stray text/whitespace (mammoth wraps real text in blocks)
        const tag = c.tagName.toUpperCase();
        if (tag === 'IMG') { parts.push(imgPara(c)); continue; }
        if (tag === 'UL' || tag === 'OL') { const numId = tag === 'OL' ? 2 : 1; c.querySelectorAll(':scope > li').forEach(li => parts.push(listPara(li, numId))); continue; }
        if (tag === 'TABLE') { parts.push(tableXml(c)); continue; }
        if (BLOCK[tag]) { const im = c.querySelector && c.querySelector('img'); if (im && !c.textContent.trim()) { parts.push(imgPara(im)); continue; } parts.push(para(c)); continue; }
        // unknown container → recurse so nested blocks are caught
        walk(c);
      }
    }
    walk(root);
    if (!parts.length) parts.push('<w:p/>');
    return parts.join('');
  }

  function ctXml() {
    let imgDefs = '';
    if (IMGS.some(i => i.ext === 'png')) imgDefs += '<Default Extension="png" ContentType="image/png"/>';
    if (IMGS.some(i => i.ext === 'jpeg')) imgDefs += '<Default Extension="jpeg" ContentType="image/jpeg"/>';
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
      '<Default Extension="xml" ContentType="application/xml"/>' + imgDefs +
      '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>' +
      '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>' +
      '<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>';
  }
  const RELS = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
    '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>';
  function docRelsXml() {
    let img = '';
    IMGS.forEach(im => { img += '<Relationship Id="' + im.rId + '" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image' + im.id + '.' + im.ext + '"/>'; });
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' +
      '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>' + img + '</Relationships>';
  }
  function heading(id, name, sz, color) {
    return '<w:style w:type="paragraph" w:styleId="' + id + '"><w:name w:val="' + name + '"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="80"/></w:pPr>' +
      '<w:rPr><w:b/><w:color w:val="' + color + '"/><w:sz w:val="' + sz + '"/></w:rPr></w:style>';
  }
  const STYLES = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">' +
    '<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>' +
    '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>' +
    heading('Heading1', 'heading 1', '40', '16233F') + heading('Heading2', 'heading 2', '32', '1F3A66') +
    heading('Heading3', 'heading 3', '28', '2B579A') + heading('Heading4', 'heading 4', '24', '2B579A') +
    heading('Heading5', 'heading 5', '22', '444444') + heading('Heading6', 'heading 6', '22', '444444') +
    '<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:ind w:left="720"/></w:pPr></w:style>' +
    '<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/></w:style></w:styles>';
  const NUMBERING = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
    '<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">' +
    '<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#8226;"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>' +
    '<w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl></w:abstractNum>' +
    '<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num></w:numbering>';

  function documentXml(root) {
    return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>' +
      '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">' +
      '<w:body>' + bodyXml(root) +
      '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr>' +
      '</w:body></w:document>';
  }

  // Returns a Promise resolving to a base64 .docx string. JSZip is passed in (vendored).
  function build(rootEl, JSZip) {
    IMGS = [];
    const docXml = documentXml(rootEl);          // populates IMGS as it walks
    const zip = new JSZip();
    zip.file('[Content_Types].xml', ctXml());
    zip.folder('_rels').file('.rels', RELS);
    const w = zip.folder('word');
    w.file('document.xml', docXml);
    w.file('styles.xml', STYLES);
    w.file('numbering.xml', NUMBERING);
    w.folder('_rels').file('document.xml.rels', docRelsXml());
    if (IMGS.length) { const media = w.folder('media'); IMGS.forEach(im => media.file('image' + im.id + '.' + im.ext, im.data, { base64: true })); }
    return zip.generateAsync({ type: 'base64', compression: 'DEFLATE' });
  }

  const API = { build, documentXml, bodyXml };
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  global.BrahmDocx = API;
})(typeof window !== 'undefined' ? window : globalThis);
