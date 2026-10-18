/* BRAHM Office Web — zero-dependency static server. Serves ./public on PORT (default 8080). */
const http = require('http'), fs = require('fs'), path = require('path'), url = require('url');
const ROOT = path.join(__dirname, 'public');
const PORT = parseInt(process.env.PORT || process.argv[2] || '8080', 10);
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon', '.ttf': 'font/ttf', '.woff': 'font/woff', '.woff2': 'font/woff2',
  '.wasm': 'application/wasm', '.map': 'application/json', '.txt': 'text/plain; charset=utf-8'
};
http.createServer((req, res) => {
  let p = decodeURIComponent(url.parse(req.url).pathname);
  if (p === '/' || p === '') p = '/index.html';
  let fp = path.normalize(path.join(ROOT, p));
  if (!fp.startsWith(ROOT)) { res.writeHead(403); return res.end('Forbidden'); }
  fs.stat(fp, (err, st) => {
    if (err || !st.isFile()) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(fp).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    fs.createReadStream(fp).pipe(res);
  });
}).listen(PORT, () => console.log('BRAHM Office Web running on http://0.0.0.0:' + PORT));
