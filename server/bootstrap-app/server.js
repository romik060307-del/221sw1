'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const host = process.env.HOST || '127.0.0.1';
const port = Number(process.env.PORT || 8080);
const startedAt = new Date().toISOString();
const html = `<!doctype html><html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#123d34"><title>OHLI Server</title><style>*{box-sizing:border-box}body{margin:0;background:#f3f7f5;color:#173b34;font:16px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;min-height:100vh;display:grid;place-items:center;padding:24px}.card{width:min(100%,640px);background:white;border:1px solid #dbe7e1;border-radius:24px;padding:30px;box-shadow:0 18px 55px #173b3414}.brand{font-size:13px;letter-spacing:.16em;font-weight:800;color:#23765f}.status{display:inline-flex;align-items:center;gap:8px;background:#e5f5ed;color:#176b47;border-radius:999px;padding:7px 12px;font-size:13px;font-weight:700}.dot{width:8px;height:8px;border-radius:50%;background:#1d9b61}h1{font-size:clamp(30px,7vw,44px);line-height:1.08;margin:18px 0 12px}p{color:#62746d}.notice{margin-top:22px;padding:17px;border-radius:16px;background:#f7f4e9;color:#67562c}.meta{font-size:12px;color:#84958e;margin-top:25px;border-top:1px solid #edf1ef;padding-top:15px}code{overflow-wrap:anywhere}</style></head><body><main class="card"><div class="brand">OHLI INFRASTRUCTURE</div><div style="margin-top:22px"><span class="status"><i class="dot"></i> SERVER HOST AKTIF</span></div><h1>OHLI Social sedang menunggu backend.</h1><p>Alamat publik, HTTPS, dan layanan host sudah merespons. Halaman ini hanya halaman pemeriksaan instalasi; ini belum menjadi website sosial aslimu.</p><div class="notice"><b>Langkah terakhir:</b><br>Pasang source backend OHLI Social melalui skrip <code>deploy-backend.sh</code>. Setelah backend asli dipasang, halaman sementara ini akan digantikan oleh aplikasimu.</div><div class="meta">OHLI TUNEL Free Server Pack · <span id="time"></span></div></main><script>document.getElementById('time').textContent=new Date().toLocaleString('id-ID')</script></body></html>`;
const server = http.createServer((req, res) => {
  if (req.url === '/healthz' || req.url === '/api/health') {
    res.writeHead(200, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ ok: true, service: 'ohli-bootstrap', state: 'backend-not-installed', startedAt, time: new Date().toISOString() }));
  }
  if (req.url === '/readyz') {
    res.writeHead(503, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
    return res.end(JSON.stringify({ ready: false, reason: 'OHLI Social backend has not been deployed yet.' }));
  }
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { allow: 'GET, HEAD' });
    return res.end('Method Not Allowed');
  }
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
  if (req.method === 'HEAD') return res.end();
  res.end(html);
});
server.listen(port, host, () => console.log(`OHLI bootstrap status server listening on ${host}:${port}`));
server.on('error', (err) => { console.error(err); process.exit(1); });
