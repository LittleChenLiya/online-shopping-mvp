"""统一 HTTP 入口：静态页面、JSON API、模块路由和请求校验。"""
import argparse
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
for folder in ('02_商品浏览与购买意向', '03_商品发布与历史', '04_交易与冻结管理'):
    sys.path.insert(0, str(ROOT / folder))
import buyer
import products
import trades
import security
from database import initialize

FRONTEND = ROOT / '01_前端界面'
ROUTES = {**buyer.ROUTES, **products.ROUTES, **trades.ROUTES, **security.ROUTES}
MAX_BODY_BYTES = 3 * 1024 * 1024


class APIError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


class Handler(BaseHTTPRequestHandler):
    error = APIError

    @staticmethod
    def text(data, key, minimum, maximum, strip=True):
        value = data.get(key, '')
        if not isinstance(value, str):
            raise APIError(400, f'{key} 必须是文本')
        value = value.strip() if strip else value
        if not minimum <= len(value) <= maximum:
            raise APIError(400, f'{key} 长度应为 {minimum}–{maximum} 个字符')
        return value

    @staticmethod
    def integer(data, key):
        value = data.get(key)
        if type(value) is not int or value <= 0:
            raise APIError(400, f'{key} 必须是正整数')
        return value

    def respond(self, status, body, mime='application/json; charset=utf-8'):
        if isinstance(body, dict):
            body = json.dumps(body, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy',
                         "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'")
        for key, value in self.extra_headers.items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def dispatch(self):
        self.extra_headers = {}
        path = urlsplit(self.path).path
        try:
            data = {}
            if self.command == 'POST':
                # 浏览器跨站表单无法添加此自定义头；本服务不开放 CORS。
                if self.headers.get('X-Requested-With') != 'ShopApp':
                    raise APIError(403, '请求缺少安全标识')
                origin = self.headers.get('Origin')
                if origin and origin != f'http://{self.headers.get("Host")}':
                    raise APIError(403, '不接受跨站请求')
                if self.headers.get('Content-Type', '').split(';')[0].strip() != 'application/json':
                    raise APIError(415, '请求须为 JSON 格式')
                try:
                    length = int(self.headers.get('Content-Length', '0'))
                except ValueError:
                    raise APIError(400, '无效的请求长度')
                if not 0 < length <= MAX_BODY_BYTES:
                    raise APIError(413, '请求内容为空或超过大小限制')
                try:
                    data = json.loads(self.rfile.read(length))
                except (ValueError, UnicodeDecodeError):
                    raise APIError(400, 'JSON 格式错误')
                if not isinstance(data, dict):
                    raise APIError(400, 'JSON 须为对象')
            route = ROUTES.get((self.command, path))
            if route:
                self.respond(200, route(self, data))
            elif self.command == 'GET' and path in ('/', '/index.html', '/app.js', '/style.css'):
                file = FRONTEND / ('index.html' if path == '/' else path.lstrip('/'))
                mime = {'.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css'}[file.suffix]
                self.respond(200, file.read_bytes(), mime + '; charset=utf-8')
            else:
                raise APIError(404, '页面或接口不存在')
        except APIError as exc:
            self.respond(exc.status, {'error': exc.message})
        except Exception:
            import traceback
            traceback.print_exc()
            self.respond(500, {'error': '服务暂时出错，请查看终端日志'})

    do_GET = dispatch
    do_POST = dispatch


def main():
    parser = argparse.ArgumentParser(description='在线购物系统（基线需求）')
    parser.add_argument('--port', type=int, default=8000)
    args = parser.parse_args()
    initialize(security.hash_password(os.environ.get('SHOP_INITIAL_PASSWORD', 'seller12345')))
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    print(f'系统已启动：http://127.0.0.1:{args.port}', flush=True)
    print('首次账号：seller；初始密码：seller12345（可通过环境变量指定）。按 Ctrl+C 停止。', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\n系统已停止。')
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
