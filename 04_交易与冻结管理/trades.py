"""成员四：选择意向买家冻结商品，线下成交或失败后更新状态。"""
from database import connection
from security import require_seller


def list_trades(request, data):
    require_seller(request)
    with connection() as conn:
        current = conn.execute("SELECT * FROM products WHERE status IN ('active','frozen')").fetchone()
        intents = conn.execute('''SELECT i.*,p.name AS product_name FROM intents i
                                 JOIN products p ON p.id=i.product_id ORDER BY i.id DESC''').fetchall()
        events = conn.execute('''SELECT e.*,p.name AS product_name,i.buyer_name
                                FROM trade_events e JOIN products p ON p.id=e.product_id
                                JOIN intents i ON i.id=e.intent_id ORDER BY e.id DESC''').fetchall()
    return {'product': dict(current) if current else None,
            'intents': [dict(row) for row in intents], 'events': [dict(row) for row in events]}


def update_trade(request, data):
    require_seller(request)
    product_id = request.integer(data, 'product_id')
    action = request.text(data, 'action', 1, 20)
    if action not in ('freeze', 'success', 'failure'):
        raise request.error(400, '无效的交易操作')
    with connection(write=True) as conn:
        product = conn.execute('SELECT * FROM products WHERE id=?', (product_id,)).fetchone()
        if product is None:
            raise request.error(404, '商品不存在')
        if action == 'freeze':
            if product['status'] != 'active':
                raise request.error(409, '只有在售商品可以冻结')
            intent_id = request.integer(data, 'intent_id')
            intent = conn.execute('SELECT * FROM intents WHERE id=? AND product_id=?',
                                  (intent_id, product_id)).fetchone()
            if intent is None or intent['status'] != 'pending':
                raise request.error(409, '请选择该商品的有效购买意向')
            conn.execute("UPDATE products SET status='frozen',selected_intent_id=? WHERE id=?",
                         (intent_id, product_id))
            conn.execute("UPDATE intents SET status='selected' WHERE id=?", (intent_id,))
        else:
            if product['status'] != 'frozen' or product['selected_intent_id'] is None:
                raise request.error(409, '商品必须先选择买家并冻结，才能处理交易结果')
            intent_id = product['selected_intent_id']
            if action == 'success':
                conn.execute("UPDATE products SET status='sold',sold_at=datetime('now','localtime') WHERE id=?",
                             (product_id,))
                conn.execute("UPDATE intents SET status=CASE WHEN id=? THEN 'completed' ELSE 'closed' END WHERE product_id=?",
                             (intent_id, product_id))
            else:
                conn.execute("UPDATE products SET status='active',selected_intent_id=NULL WHERE id=?", (product_id,))
                conn.execute("UPDATE intents SET status='pending' WHERE id=?", (intent_id,))
        conn.execute('INSERT INTO trade_events(product_id,intent_id,action) VALUES (?,?,?)',
                     (product_id, intent_id, action))
    messages = {'freeze': '商品已冻结，请在线下完成交易',
                'success': '交易成功，商品已下架，可以发布下一件商品',
                'failure': '交易失败，商品已恢复上线'}
    return {'message': messages[action]}


ROUTES = {
    ('GET', '/api/trades'): list_trades,
    ('POST', '/api/trade'): update_trade,
}
