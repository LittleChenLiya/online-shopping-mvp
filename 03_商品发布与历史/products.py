"""成员三：商品发布、图片和金额校验、商品历史。"""
import base64
import re
import sqlite3
from decimal import Decimal, InvalidOperation
from database import connection
from security import require_seller

MAX_IMAGE_BYTES = 2 * 1024 * 1024


def parse_price(request, value):
    if not isinstance(value, str) or not re.fullmatch(r'\d{1,7}(\.\d{1,2})?', value):
        raise request.error(400, '价格须为正数，最多两位小数')
    try:
        amount = Decimal(value)
        if not Decimal('0') < amount <= Decimal('9999999.99'):
            raise InvalidOperation
        return int(amount * 100)
    except InvalidOperation:
        raise request.error(400, '价格不在有效范围内')


def validate_image(request, value):
    if not isinstance(value, str):
        raise request.error(400, '请上传商品图片')
    match = re.fullmatch(r'data:image/(png|jpeg|webp);base64,([A-Za-z0-9+/=]+)', value)
    if not match or len(value) > MAX_IMAGE_BYTES * 4 // 3 + 100:
        raise request.error(400, '图片仅支持 PNG、JPEG、WebP，最大 2 MB')
    try:
        raw = base64.b64decode(match[2], validate=True)
    except ValueError:
        raise request.error(400, '图片编码无效')
    signatures = {
        'png': raw.startswith(b'\x89PNG\r\n\x1a\n'),
        'jpeg': raw.startswith(b'\xff\xd8\xff'),
        'webp': raw[:4] == b'RIFF' and raw[8:12] == b'WEBP',
    }
    if len(raw) > MAX_IMAGE_BYTES or not signatures[match[1]]:
        raise request.error(400, '图片文件格式或大小不符合要求')
    return value


def publish_product(request, data):
    require_seller(request)
    name = request.text(data, 'name', 1, 80)
    description = request.text(data, 'description', 1, 2000)
    cents = parse_price(request, data.get('price'))
    image = validate_image(request, data.get('image'))
    with connection(write=True) as conn:
        if conn.execute("SELECT 1 FROM products WHERE status IN ('active','frozen')").fetchone():
            raise request.error(409, '已有未售出商品，成交下架后才能发布下一件')
        try:
            result = conn.execute(
                "INSERT INTO products(name,description,image,price_cents,status) VALUES (?,?,?,?,'active')",
                (name, description, image, cents))
        except sqlite3.IntegrityError:
            raise request.error(409, '只能存在一个未售出的商品')
    return {'id': result.lastrowid, 'message': '商品已发布'}


def list_products(request, data):
    require_seller(request)
    with connection() as conn:
        rows = conn.execute('SELECT * FROM products ORDER BY id DESC').fetchall()
    return {'products': [dict(row) for row in rows]}


ROUTES = {
    ('POST', '/api/products'): publish_product,
    ('GET', '/api/products'): list_products,
}
