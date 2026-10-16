// Copies the shared web engine (same code as the Android app) into the Electron bundle.
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'webapp');
const dst = path.join(__dirname, 'webapp');
fs.rmSync(dst, { recursive: true, force: true });
fs.cpSync(src, dst, { recursive: true });
console.log('Copied web engine →', dst);
