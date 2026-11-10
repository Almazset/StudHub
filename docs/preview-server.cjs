const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const staticRoot = path.join(root, 'src', 'main', 'resources', 'static');
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf'};
http.createServer(async (request, response) => {
  try {
    if (!['GET','HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    if (pathname === '/') { response.writeHead(302,{Location:'/docs/studhub-preview.html'}); response.end(); return; }
    const file = path.resolve(root, `.${pathname}`);
    const previewFile = [path.join(__dirname,'studhub-preview.html'),path.join(__dirname,'studhub-preview-enhancements.js')].includes(file);
    if (!previewFile && !file.startsWith(staticRoot + path.sep)) { response.writeHead(404); response.end(); return; }
    const bytes = await fs.readFile(file);
    response.writeHead(200,{'Content-Type':types[path.extname(file)] || 'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
    response.end(request.method === 'HEAD' ? undefined : bytes);
  } catch { response.writeHead(404); response.end(); }
}).listen(8765,'127.0.0.1',() => process.stdout.write('StudHub preview: http://127.0.0.1:8765/docs/studhub-preview.html\n'));
