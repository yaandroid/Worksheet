/* BRAHM Office — Web/VPS file bridge.
   Implements the SAME window.AndroidBridge API the engine uses on Android,
   but with browser APIs: open via <input type=file>, save via download,
   share via the Web Share API (falls back to download). Must load BEFORE engine.js/app.js. */
(function () {
  function readAsB64(file, cb) {
    var r = new FileReader();
    r.onload = function () { var s = r.result || ''; var i = s.indexOf(','); cb(i >= 0 ? s.slice(i + 1) : s, file.name); };
    r.onerror = function () { cb(null, file.name); };
    r.readAsDataURL(file);
  }
  function pick(accept, cb) {
    var inp = document.createElement('input');
    inp.type = 'file'; if (accept) inp.accept = accept;
    inp.style.position = 'fixed'; inp.style.left = '-9999px';
    document.body.appendChild(inp);
    inp.onchange = function () {
      var f = inp.files && inp.files[0];
      if (f) readAsB64(f, cb);
      setTimeout(function () { try { document.body.removeChild(inp); } catch (e) {} }, 0);
    };
    inp.click();
  }
  function b64ToBlob(b64, mime) {
    var bin = atob(b64), len = bin.length, u = new Uint8Array(len);
    for (var i = 0; i < len; i++) u[i] = bin.charCodeAt(i);
    return new Blob([u], { type: mime || 'application/octet-stream' });
  }
  function download(name, blob) {
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a'); a.href = url; a.download = name || 'file';
    document.body.appendChild(a); a.click();
    setTimeout(function () { try { document.body.removeChild(a); URL.revokeObjectURL(url); } catch (e) {} }, 1500);
  }
  var acceptFor = {
    pdf: 'application/pdf,.pdf',
    image: 'image/*',
    sheet: '.xlsx,.xls,.csv',
    word: '.docx,.doc'
  };
  window.AndroidBridge = {
    ready: function () {},
    pickFile: function () { pick('', function (b64, name) { if (b64 && window.BrahmOpen) window.BrahmOpen(b64, name); }); },
    pickFor: function (kind) { pick(acceptFor[kind] || '', function (b64, name) { if (b64 && window.BrahmPicked) window.BrahmPicked(b64, name); }); },
    saveFile: function (name, mime, b64) { try { download(name, b64ToBlob(b64, mime)); } catch (e) {} },
    shareBytes: function (name, mime, b64) {
      try {
        var blob = b64ToBlob(b64, mime);
        if (navigator.canShare && window.File) {
          var file = new File([blob], name, { type: mime });
          if (navigator.canShare({ files: [file] })) { navigator.share({ files: [file], title: name }).catch(function () { download(name, blob); }); return; }
        }
        download(name, blob);
      } catch (e) { try { download(name, b64ToBlob(b64, mime)); } catch (e2) {} }
    },
    // Browser build has no on-device ML Kit OCR — tell the engine gracefully (it already handles empty).
    ocr: function (b64, lang) { if (window.BrahmOcrResult) window.BrahmOcrResult('', 'OCR is available in the BRAHM Office mobile app'); }
  };
})();
