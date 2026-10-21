"""固定单一卖家，无用户注册。密码采用带盐 PBKDF2 哈希。"""
import hashlib
import hmac
import secrets
import time
from http.cookies import SimpleCookie

from database import connection

SESSION_SECONDS = 8 * 60 * 60
PASSWORD_ITERATIONS = 260000


def hash_password(password):
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac('sha256', password.encode(), salt.encode(),
                                 PASSWORD_ITERATIONS).hex()
    return f'{PASSWORD_ITERATIONS}${salt}${digest}'


def verify_password(password, stored):
    rounds, salt, expected = stored.split('$')
    actual = hashlib.pbkdf2_hmac('sha256', password.encode(), salt.encode(),
                                 int(rounds)).hex()
    return hmac.compare_digest(actual, expected)


def session_hash(request):
    cookie = SimpleCookie()
    try:
        cookie.load(request.headers.get('Cookie', ''))
        token = cookie['shop_session'].value if 'shop_session' in cookie else ''
    except Exception:
        token = ''
    return hashlib.sha256(token.encode()).hexdigest()


def logged_in(request):
    with connection() as conn:
        return conn.execute('SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?',
                            (session_hash(request), time.time())).fetchone() is not None


def require_seller(request):
    if not logged_in(request):
        raise request.error(401, '请先登录卖家账号')


def login(request, data):
    username = request.text(data, 'username', 1, 40)
    password = request.text(data, 'password', 1, 128, strip=False)
    with connection(write=True) as conn:
        seller = conn.execute('SELECT * FROM seller WHERE id=1').fetchone()
        if username != seller['username'] or not verify_password(password, seller['password_hash']):
            raise request.error(401, '账号或密码错误')
        token = secrets.token_urlsafe(32)
        conn.execute('DELETE FROM sessions WHERE expires_at<=?', (time.time(),))
        conn.execute('INSERT INTO sessions VALUES (?,?)',
                     (hashlib.sha256(token.encode()).hexdigest(), time.time() + SESSION_SECONDS))
    request.extra_headers['Set-Cookie'] = (
        f'shop_session={token}; HttpOnly; SameSite=Strict; Path=/; Max-Age={SESSION_SECONDS}')
    return {'message': '登录成功'}


def logout(request, data):
    with connection(write=True) as conn:
        conn.execute('DELETE FROM sessions WHERE token_hash=?', (session_hash(request),))
    request.extra_headers['Set-Cookie'] = 'shop_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0'
    return {'message': '已退出登录'}


def change_password(request, data):
    require_seller(request)
    old = request.text(data, 'old_password', 1, 128, strip=False)
    new = request.text(data, 'new_password', 8, 128, strip=False)
    if old == new:
        raise request.error(400, '新密码不能与旧密码相同')
    with connection(write=True) as conn:
        # 在同一写事务内再次验证会话，避免旧会话并发修改密码。
        if not conn.execute('SELECT 1 FROM sessions WHERE token_hash=? AND expires_at>?',
                            (session_hash(request), time.time())).fetchone():
            raise request.error(401, '登录已失效')
        seller = conn.execute('SELECT * FROM seller WHERE id=1').fetchone()
        if not verify_password(old, seller['password_hash']):
            raise request.error(400, '原密码错误')
        conn.execute('UPDATE seller SET password_hash=? WHERE id=1', (hash_password(new),))
        conn.execute('DELETE FROM sessions')
    request.extra_headers['Set-Cookie'] = 'shop_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0'
    return {'message': '密码已修改，请重新登录'}


def account(request, data):
    return {'logged_in': logged_in(request), 'username': 'seller'}


ROUTES = {
    ('GET', '/api/account'): account,
    ('POST', '/api/login'): login,
    ('POST', '/api/logout'): logout,
    ('POST', '/api/password'): change_password,
}
