#!/usr/bin/env python3
# NSO token HTTP 服务: GET /token?dataUser=0 -> 跑 provider.sh 返回 {gtoken,bulletToken,webViewVer,language,country}
# 带缓存(默认 60s),避免每次请求都重读 cookie + curl
import json, subprocess, time, sys, os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

CACHE = {}          # dataUser -> (ts, json_str)
CACHE_TTL = 60      # 秒;bbBot 频繁查时复用,gtoken 本身 ~2h
DEVICE = os.environ.get('NSO_DEVICE', '99e0fc6d')   # 手机 adb serial,deploy 时由 systemd Environment 注入

def get_token(data_user):
    now = time.time()
    if data_user in CACHE and now - CACHE[data_user][0] < CACHE_TTL:
        return CACHE[data_user][1]
    out = subprocess.run(['bash','/root/k8s-nso-token/provider.sh',DEVICE,data_user],
                         capture_output=True, text=True, timeout=90)
    js = out.stdout.strip() or '{"error":"empty"}'
    try:
        d = json.loads(js)
        if 'gtoken' in d:
            CACHE[data_user] = (now, js)
    except: pass
    return js

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        du = q.get('dataUser', ['0'])[0]
        if not urlparse(self.path).path.startswith('/token'):
            self.send_response(404); self.end_headers(); return
        try:
            body = get_token(du).encode()
            self.send_response(200)
        except Exception as e:
            body = json.dumps({'error': str(e)}).encode()
            self.send_response(500)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    print(f'listening 0.0.0.0:{port}', flush=True)
    ThreadingHTTPServer(('0.0.0.0', port), H).serve_forever()
