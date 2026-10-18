/* Copies the shared webapp engine into web/public and injects the web file-bridge. */
const fs = require('fs'), path = require('path');
const SRC = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'webapp');
const OUT = path.join(__dirname, 'public');
function rmrf(d) { if (fs.existsSync(d)) fs.rmSync(d, { recursive: true, force: true }); }
function copyDir(s, d) {
  fs.mkdirSync(d, { recursive: true });
  for (const e of fs.readdirSync(s, { withFileTypes: true })) {
    const sp = path.join(s, e.name), dp = path.join(d, e.name);
    if (e.isDirectory()) copyDir(sp, dp); else fs.copyFileSync(sp, dp);
  }
}
rmrf(OUT); copyDir(SRC, OUT);
// copy the web bridge in
fs.copyFileSync(path.join(__dirname, 'bridge-web.js'), path.join(OUT, 'bridge-web.js'));
// inject <script src="bridge-web.js"> right before engine.js so AndroidBridge exists first
let html = fs.readFileSync(path.join(OUT, 'index.html'), 'utf8');
if (html.indexOf('bridge-web.js') < 0) {
  html = html.replace('<script src="engine.js"></script>', '<script src="bridge-web.js"></script>\n  <script src="engine.js"></script>');
}
fs.writeFileSync(path.join(OUT, 'index.html'), html);
console.log('web/public built with web bridge injected');
