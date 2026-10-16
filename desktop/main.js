// BRAHM Office — desktop (Electron) host. Wraps the shared web engine and provides
// the same bridge the Android app exposes (open/save/share), backed by native file dialogs.
const { app, BrowserWindow, ipcMain, dialog, Menu, shell } = require('electron');
const fs = require('fs');
const path = require('path');

const OFFICE_FILTERS = [
  { name: 'All documents', extensions: ['pdf', 'xlsx', 'xls', 'csv', 'docx', 'doc', 'pptx', 'ppt', 'txt', 'md', 'html', 'htm'] },
  { name: 'PDF', extensions: ['pdf'] },
  { name: 'Excel', extensions: ['xlsx', 'xls', 'csv'] },
  { name: 'Word', extensions: ['docx', 'doc'] },
  { name: 'PowerPoint', extensions: ['pptx', 'ppt'] },
  { name: 'All files', extensions: ['*'] }
];

let mainWindow = null;
let pendingOpenPath = null;   // file passed on launch (double-click / "open with")

function filePayload(p) {
  try { const buf = fs.readFileSync(p); return { b64: buf.toString('base64'), name: path.basename(p) }; }
  catch (e) { return null; }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280, height: 860, minWidth: 760, minHeight: 540,
    backgroundColor: '#16233F',
    title: 'BRAHM Office',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: false, nodeIntegration: false, sandbox: false
    }
  });
  mainWindow.loadFile(path.join(__dirname, 'webapp', 'index.html'));
  // Once the page is ready, feed any file the app was launched with.
  mainWindow.webContents.on('did-finish-load', () => {
    const p = pendingOpenPath; pendingOpenPath = null;
    if (p) { const f = filePayload(p); if (f) mainWindow.webContents.executeJavaScript(`window.BrahmOpen(${JSON.stringify(f.b64)}, ${JSON.stringify(f.name)});`); }
  });
  mainWindow.on('closed', () => { mainWindow = null; });
}

function buildMenu() {
  const isMac = process.platform === 'darwin';
  const template = [
    ...(isMac ? [{ label: 'BRAHM Office', submenu: [{ role: 'about' }, { type: 'separator' }, { role: 'hide' }, { role: 'quit' }] }] : []),
    {
      label: 'File',
      submenu: [
        { label: 'Open…', accelerator: 'CmdOrCtrl+O', click: () => mainWindow && mainWindow.webContents.executeJavaScript('window.AndroidBridge && window.AndroidBridge.pickFile && window.AndroidBridge.pickFile();') },
        { type: 'separator' },
        isMac ? { role: 'close' } : { role: 'quit' }
      ]
    },
    { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' }, { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] },
    { label: 'View', submenu: [{ role: 'reload' }, { role: 'togglefullscreen' }, { type: 'separator' }, { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' }] },
    { label: 'Window', submenu: [{ role: 'minimize' }, { role: 'zoom' }, ...(isMac ? [{ role: 'front' }] : [{ role: 'close' }])] }
  ];
  return Menu.buildFromTemplate(template);
}

// macOS: file opened from Finder
app.on('open-file', (e, p) => { e.preventDefault(); if (mainWindow) { const f = filePayload(p); if (f) mainWindow.webContents.executeJavaScript(`window.BrahmOpen(${JSON.stringify(f.b64)}, ${JSON.stringify(f.name)});`); } else pendingOpenPath = p; });
// Windows/Linux: file path in argv
if (process.argv.length > 1) { const a = process.argv[process.argv.length - 1]; if (a && !a.startsWith('-') && fs.existsSync(a) && fs.statSync(a).isFile()) pendingOpenPath = a; }

app.whenReady().then(() => {
  Menu.setApplicationMenu(buildMenu());
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

// ---- bridge handlers ----
ipcMain.handle('pick-file', async () => {
  const r = await dialog.showOpenDialog(mainWindow, { properties: ['openFile'], filters: OFFICE_FILTERS });
  if (r.canceled || !r.filePaths[0]) return null; return filePayload(r.filePaths[0]);
});
ipcMain.handle('pick-for', async (e, kind) => {
  const filters = kind === 'pdf' ? [{ name: 'PDF', extensions: ['pdf'] }]
    : kind === 'image' ? [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'] }]
    : [{ name: 'All files', extensions: ['*'] }];
  const r = await dialog.showOpenDialog(mainWindow, { properties: ['openFile'], filters });
  if (r.canceled || !r.filePaths[0]) return null; return filePayload(r.filePaths[0]);
});
ipcMain.handle('save-file', async (e, { name, mime, b64 }) => {
  const r = await dialog.showSaveDialog(mainWindow, { defaultPath: name || 'document' });
  if (r.canceled || !r.filePath) return false;
  try { fs.writeFileSync(r.filePath, Buffer.from(b64, 'base64')); return true; }
  catch (err) { dialog.showErrorBox('Save failed', String(err)); return false; }
});
