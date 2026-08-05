// server.js (fixed)
const http = require('http');
const fs = require('fs');
const path = require('path');
//const url = require('url');

const PORT = process.env.PORT || 8081;
const PUBLIC_PAGES = path.join(__dirname, 'public/pages');
const PUBLIC_ASSETS = path.join(__dirname, 'public/assets');

const BACKEND_HOST = process.env.BACKEND_HOST || 'localhost';
const BACKEND_PORT = parseInt(process.env.BACKEND_PORT || '8080', 10);

const mimeTypes = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml',
    '.woff': 'font/woff',
    '.woff2': 'font/woff2',
    '.tpl': 'image/tpl',
    '.ttf': 'font/ttf',
    '.otf': 'font/otf'
};

function proxyToBackend(req, res) {
    let urlToCall = req.url;

    if(urlToCall.startsWith("/api/"))
    {
        urlToCall = urlToCall.substring(4);
    }

    const options = {
        hostname: BACKEND_HOST,
        port: BACKEND_PORT,
        path: urlToCall,
        method: req.method,
        headers: Object.assign({}, req.headers, { host: `${BACKEND_HOST}:${BACKEND_PORT}` })
    };

    const backendReq = http.request(options, backendRes => {
        // Forward status and headers
        res.writeHead(backendRes.statusCode, backendRes.headers);
        backendRes.pipe(res, { end: true });
    });

    backendReq.on('error', err => {
        console.error('Proxy error:', err && err.message);
        if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json; charset=UTF-8' });
        res.end(JSON.stringify({ error: 'Bad Gateway', message: err.message }));
    });

    // Pipe request body to backend
    req.pipe(backendReq, { end: true });
}

function serveFile(filePath, res) {
    const ext = path.extname(filePath);
    const contentTypeBase = mimeTypes[ext] || 'application/octet-stream';
    const textBasedTypes = ['text/html', 'text/css', 'application/javascript'];
    const isText = textBasedTypes.includes(contentTypeBase) || contentTypeBase.startsWith('text/');
    const encoding = isText ? 'utf8' : null;
    const contentType = isText ? `${contentTypeBase}; charset=UTF-8` : contentTypeBase;

    fs.readFile(filePath, encoding, (err, content) => {
        if (err) {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=UTF-8' });
            res.end('404 Not Found');
            return;
        }
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(content);
    });
}

const server = http.createServer((req, res) => {
    const host = req.headers.host || 'localhost';
    const protocol = req.headers['x-forwarded-proto'] || 'http';
    const parsedUrl = new URL(req.url, `${protocol}://${host}`);
    const pathname = parsedUrl.pathname || '/';

    // 1. Proxy API calls AND SockJS info/transport calls
    // SockJS usually calls /websocket/info or /websocket/<server>/<session>/xhr
    if (pathname.startsWith('/websocket')) {
        return proxyToBackend(req, res);
    }

    // Proxy API calls
    if (pathname.startsWith('/api')) {
        return proxyToBackend(req, res);
    }

    // Static assets
    if (pathname.startsWith('/assets/')) {
        const rel = pathname.replace('/assets/', '');
        const filePath = path.join(PUBLIC_ASSETS, rel);
        return serveFile(filePath, res);
    }

    // Pages
    let filePath = path.join(PUBLIC_PAGES, pathname);
    if (pathname === '/' || pathname === '') {
        filePath = path.join(PUBLIC_PAGES, 'index.html');
    } else if (!path.extname(filePath)) {
        filePath += '.html';
    }
    return serveFile(filePath, res);
});

// WebSocket upgrade proxying
server.on('upgrade', (req, socket, head) => {
    // 1. Use the WHATWG URL API instead of url.parse
    const baseURL = `http://${req.headers.host}`;
    const parsedUrl = new URL(req.url, baseURL);
    const pathname = parsedUrl.pathname;

    if (!pathname.startsWith('/websocket')) {
        socket.destroy();
        return;
    }

    // 1. Handle errors on the CLIENT socket (Browser <-> Node)
    socket.on('error', (err) => {
        console.error('Client Socket Error:', err.message);
        socket.destroy();
    });

    const options = {
        hostname: BACKEND_HOST,
        port: BACKEND_PORT,
        path: req.url,
        method: 'GET',
        headers: {
            ...req.headers,
            'Connection': 'Upgrade',
            'Upgrade': 'websocket'
        }
    };

    const backendReq = http.request(options);

    backendReq.on('upgrade', (backendRes, backendSocket, backendHead) => {
        // Guard 2: Handle errors on the node-to-backend socket
        backendSocket.on('error', (err) => {
            console.error('Backend Socket Error:', err.message);
            socket.destroy();
        });

        // 1. Manually write the handshake response to the browser
        // Guard 3: Only write if the browser socket is still open
        if (socket.writable) {
            socket.write('HTTP/1.1 101 Switching Protocols\r\n' +
                'Upgrade: websocket\r\n' +
                'Connection: Upgrade\r\n' +
                '\r\n');
        } else {
            backendSocket.destroy();
            return;
        }

        // 2. Handle initial data
        if (backendHead && backendHead.length) backendSocket.unshift(backendHead);

        // 3. Robust bidirectional piping
        // We add error listeners to both to prevent the ECONNRESET from crashing the server
        backendSocket.on('error', () => socket.destroy());
        socket.on('error', () => backendSocket.destroy());

        backendSocket.pipe(socket);
        socket.pipe(backendSocket);
    });

    backendReq.on('error', (err) => {
        console.error('WebSocket Upgrade error:', err.message);
        socket.destroy();
    });

    backendReq.write(head);
    backendReq.end();
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on port ${PORT}`);
    console.log(`Proxying /api -> http://${BACKEND_HOST}:${BACKEND_PORT}`);
    console.log(`Proxying /websocket -> ws://${BACKEND_HOST}:${BACKEND_PORT}`);
});
