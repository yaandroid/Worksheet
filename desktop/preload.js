// Exposes the same window.AndroidBridge API the web engine already uses on Android,
// so the identical app.js runs unchanged on the desktop.
const { ipcRenderer } = require('electron');

window.AndroidBridge = {
  ready() {},
  pickFile() { ipcRenderer.invoke('pick-file').then(f => { if (f && window.BrahmOpen) window.BrahmOpen(f.b64, f.name); }); },
  pickFor(kind) { ipcRenderer.invoke('pick-for', kind).then(f => { if (f && window.BrahmPicked) window.BrahmPicked(f.b64, f.name); }); },
  saveFile(name, mime, b64) { ipcRenderer.invoke('save-file', { name, mime, b64 }); },
  // Desktop has no share sheet — "share" simply saves the file where the user chooses.
  shareBytes(name, mime, b64) { ipcRenderer.invoke('save-file', { name, mime, b64 }); },
  // On-device OCR is a mobile (ML Kit) feature; on desktop we tell the user gracefully.
  ocr(b64, lang) { if (window.BrahmOcrResult) window.BrahmOcrResult('', 'OCR is available in the BRAHM Office mobile app'); }
};
