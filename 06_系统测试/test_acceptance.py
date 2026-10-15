"""第六成员新增的跨模块验收测试；仅复用第五模块的测试夹具。"""
import http.client
import sys
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '05_卖家账号与系统集成'))
import test_system as regression
import database


class AcceptanceTests(unittest.TestCase):
    # 复用启动服务、临时数据库及API助手，不继承原来的测试方法，避免重复计数。
    setUpClass = classmethod(regression.ShoppingTest.setUpClass.__func__)
    tearDownClass = classmethod(regression.ShoppingTest.tearDownClass.__func__)
    setUp = regression.ShoppingTest.setUp
    tearDown = regression.ShoppingTest.tearDown
    request = regression.ShoppingTest.request
    login = regression.ShoppingTest.login
    publish = regression.ShoppingTest.publish
    intent = regression.ShoppingTest.intent
    trade = regression.ShoppingTest.trade

    def prepare_product(self):
        self.login()
        code, result = self.publish()
        self.assertEqual(code, 200)
        return result['id']

    def test_concurrent_duplicate_intent(self):
        """A01：同一买家并发提交，恰好一条意向被保存。"""
        product = self.prepare_product()
        with ThreadPoolExecutor(max_workers=2) as pool:
            codes = list(pool.map(lambda _: self.intent(product)[0], range(2)))
        self.assertEqual(sorted(codes), [200, 409])
        self.assertEqual(len(self.request('/api/trades')[1]['intents']), 1)

    def test_concurrent_freeze_selects_one_buyer(self):
        """A02：两位买家同时被冻结，只能选中其中一位。"""
        product = self.prepare_product()
        buyers = [self.intent(product, phone)[1]['id'] for phone in ('13800138000', '13900139000')]
        with ThreadPoolExecutor(max_workers=2) as pool:
            codes = list(pool.map(lambda buyer: self.trade(product, 'freeze', buyer)[0], buyers))
        self.assertEqual(sorted(codes), [200, 409])
        result = self.request('/api/trades')[1]
        self.assertEqual(result['product']['status'], 'frozen')
        self.assertEqual(sorted(i['status'] for i in result['intents']), ['pending', 'selected'])
        self.assertEqual(len(result['events']), 1)
        chosen = next(i for i in result['intents'] if i['status'] == 'selected')
        self.assertEqual(result['product']['selected_intent_id'], chosen['id'])

    def test_conflicting_trade_results_are_consistent(self):
        """A03：成功与失败并发请求，最多执行一个且多表状态一致。"""
        product = self.prepare_product()
        buyer = self.intent(product)[1]['id']
        self.assertEqual(self.trade(product, 'freeze', buyer)[0], 200)
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda action: (action, self.trade(product, action)[0]), ('success', 'failure')))
        self.assertEqual(sorted(code for _, code in results), [200, 409])
        winner = next(action for action, code in results if code == 200)
        result = self.request('/api/trades')[1]
        self.assertEqual(len(result['events']), 2)
        self.assertEqual(result['events'][0]['action'], winner)
        history = self.request('/api/products')[1]['products'][0]
        if winner == 'success':
            self.assertIsNone(result['product'])
            self.assertEqual(history['status'], 'sold')
            self.assertEqual(result['intents'][0]['status'], 'completed')
        else:
            self.assertEqual(result['product']['status'], 'active')
            self.assertEqual(history['status'], 'active')
            self.assertIsNone(result['product']['selected_intent_id'])
            self.assertEqual(result['intents'][0]['status'], 'pending')

    def test_rejected_operation_does_not_change_state(self):
        """A04：无效意向导致冻结失败，不改变商品、意向及日志。"""
        product = self.prepare_product()
        self.intent(product)
        before = self.request('/api/trades')[1]
        self.assertEqual(self.trade(product, 'freeze', 999999)[0], 409)
        after = self.request('/api/trades')[1]
        self.assertEqual(before, after)

    def raw_request(self, method, path, body=None, extra=None):
        """绕开正常JSON助手，构造非法HTTP输入并读取状态码和原始响应。"""
        conn = http.client.HTTPConnection('127.0.0.1', self.port, timeout=10)
        headers = {'Content-Type': 'application/json', 'X-Requested-With': 'ShopApp'}
        headers.update(extra or {})
        try:
            conn.request(method, path, body=body, headers=headers)
            response = conn.getresponse()
            return response.status, response.read(), dict(response.getheaders())
        finally:
            conn.close()

    def test_malformed_requests(self):
        """A05：非法JSON、非对象JSON和超长请求头长度被拒绝。"""
        self.assertEqual(self.raw_request('POST', '/api/login', b'{')[0], 400)
        self.assertEqual(self.raw_request('POST', '/api/login', b'[]')[0], 400)
        self.assertEqual(self.raw_request('POST', '/api/login', b'',
                         {'Content-Length': str(3 * 1024 * 1024 + 1)})[0], 413)

    def test_static_files_and_private_paths(self):
        """A06：前端静态文件可达，数据库和任意源码路径不公开。"""
        for path, marker in (('/', b'<html'), ('/app.js', b'function'), ('/style.css', b':root')):
            with self.subTest(path=path):
                code, body, headers = self.raw_request('GET', path)
                self.assertEqual(code, 200)
                self.assertIn(marker, body)
                self.assertEqual(headers.get('X-Content-Type-Options'), 'nosniff')
        for path in ('/data/shop.db', '/05_卖家账号与系统集成/security.py', '/../README.md'):
            with self.subTest(path=path):
                # 中文URL必须百分号编码才能形成合法HTTP请求行。
                from urllib.parse import quote
                self.assertEqual(self.raw_request('GET', quote(path))[0], 404)

    def test_limits_do_not_create_product(self):
        """A07：超长名称/描述、超大图片不能生成商品记录。"""
        import base64
        self.login()
        for fields in ({'name': '名' * 81}, {'description': '描' * 2001}):
            self.assertEqual(self.publish(**fields)[0], 400)
        oversized = b'\x89PNG\r\n\x1a\n' + b'x' * (2 * 1024 * 1024)
        image = 'data:image/png;base64,' + base64.b64encode(oversized).decode()
        self.assertEqual(self.publish(image=image)[0], 400)
        self.assertIsNone(self.request('/api/product')[1]['product'])
        self.assertEqual(self.request('/api/products')[1]['products'], [])

    def test_special_characters_are_saved_as_data(self):
        """A08：含引号的名称按数据保存，不改变SQL结构。"""
        self.login()
        name = "杯'; DROP TABLE products;--"
        code, result = self.publish(name=name)
        self.assertEqual(code, 200)
        product = self.request('/api/product')[1]['product']
        self.assertEqual(product['name'], name)
        code, result = self.request('/api/intents', {'product_id': result['id'],
                                   'buyer_name': "O'Reilly", 'contact': '13800138000', 'note': ''},
                                   authenticated=False)
        self.assertEqual(code, 200)
        self.assertEqual(self.request('/api/trades')[1]['intents'][0]['buyer_name'], "O'Reilly")
        with database.connection() as conn:
            self.assertEqual(conn.execute('SELECT count(*) FROM products').fetchone()[0], 1)


if __name__ == '__main__':
    unittest.main(verbosity=2)
