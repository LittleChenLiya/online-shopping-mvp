"""成员二：公开商品查询及免注册的购买意向提交。"""
import re
import sqlite3
from database import connection, public_product


def get_current_product(request, data):
    with connection() as conn:
        row = conn.execute("SELECT * FROM products WHERE status IN ('active','frozen')").fetchone()
    return {'product': public_product(row)}


def submit_intent(request, data):
    product_id = request.integer(data, 'product_id')
    name = request.text(data, 'buyer_name', 1, 40)
    contact = request.text(data, 'contact', 6, 30)
    if not re.fullmatch(r'[0-9+()\- ]+', contact):
        raise request.error(400, '联系方式请填写电话号码')
    contact = re.sub(r'[()\- ]', '', contact)
    if not 6 <= len(contact.lstrip('+')) <= 20:
        raise request.error(400, '电话号码长度不正确')
    note = request.text(data, 'note', 0, 300)
    with connection(write=True) as conn:
        product = conn.execute('SELECT * FROM products WHERE id=?', (product_id,)).fetchone()
        if product is None or product['status'] != 'active':
            raise request.error(409, '商品当前不可购买，请刷新页面')
        try:
            result = conn.execute('INSERT INTO intents(product_id,buyer_name,contact,note) VALUES (?,?,?,?)',
                                   (product_id, name, contact, note))
        except sqlite3.IntegrityError:
            raise request.error(409, '该电话已提交过此商品的购买意向，请等待卖家联系')
    return {'id': result.lastrowid, 'message': '意向已提交，请等待卖家联系；付款与交货在线下完成'}


ROUTES = {
    ('GET', '/api/product'): get_current_product,
    ('POST', '/api/intents'): submit_intent,
}
