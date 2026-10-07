"""集成测试：使用独立临时数据库，不修改作业系统的真实数据。"""
import base64
import http.client
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor

os.environ.setdefault('SHOP_DB_PATH', str(Path(__file__).resolve().parent / '_unused_test.db'))
import database
import security
import server

PNG = 'data:image/png;base64,' + base64.b64encode(base64.b64decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=' )).decode()


class QuietHandler(server.Handler):
    def log_message(self, *args):
        pass


class ShoppingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.http = server.ThreadingHTTPServer(('127.0.0.1', 0), QuietHandler)
        cls.port = cls.http.server_address[1]
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.thread.join()

    def setUp(self):
        base = Path('D:/Codex/work/shopping-mvp/tests')
        base.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=base)
        database.DB_PATH = Path(self.temp.name) / 'test.db'
        database.initialize(security.hash_password('seller12345'))
        self.cookie = ''

    def tearDown(self):
        self.temp.cleanup()

    def request(self, path, data=None, authenticated=True, headers=None):
        client = http.client.HTTPConnection('127.0.0.1', self.port, timeout=10)
        request_headers = {'Content-Type': 'application/json', 'X-Requested-With': 'ShopApp'}
        if authenticated and self.cookie:
            request_headers['Cookie'] = self.cookie
        request_headers.update(headers or {})
        client.request('GET' if data is None else 'POST', path,
                       None if data is None else json.dumps(data), request_headers)
        response = client.getresponse()
        cookie = response.getheader('Set-Cookie')
        content = response.read()
        status = response.status
        client.close()
        if cookie and authenticated:
            self.cookie = cookie.split(';')[0]
        return status, json.loads(content)

    def login(self, password='seller12345'):
        self.assertEqual(self.request('/api/login', {'username':'seller', 'password':password})[0], 200)

    def publish(self, **changes):
        data = {'name':'手作杯', 'description':'唯一的一件', 'price':'19.90', 'image':PNG}
        data.update(changes)
        return self.request('/api/products', data)

    def intent(self, product_id, contact='13800138000'):
        return self.request('/api/intents', {'product_id':product_id,'buyer_name':'买家',
                                           'contact':contact,'note':'周末联系'}, authenticated=False)

    def trade(self, product_id, action, intent_id=None):
        data = {'product_id':product_id, 'action':action}
        if intent_id is not None:
            data['intent_id'] = intent_id
        return self.request('/api/trade', data)

    def test_full_workflow_and_history(self):
        self.assertIsNone(self.request('/api/product')[1]['product'])
        self.login()
        product_id = self.publish()[1]['id']
        self.assertEqual(self.publish()[0], 409)
        public = self.request('/api/product')[1]['product']
        self.assertEqual(public['price_cents'], 1990)
        self.assertNotIn('selected_intent_id', public)
        first = self.intent(product_id)[1]['id']
        second = self.intent(product_id, '13900139000')[1]['id']
        self.assertEqual(self.trade(product_id,'success')[0],409)
        self.assertEqual(self.trade(product_id,'freeze', first)[0],200)
        self.assertEqual(self.intent(product_id, '13700137000')[0],409)
        self.assertEqual(self.publish()[0],409)
        self.assertEqual(self.trade(product_id,'freeze', second)[0],409)
        self.assertEqual(self.trade(product_id,'failure')[0],200)
        self.assertEqual(self.request('/api/product')[1]['product']['status'],'active')
        self.assertEqual(self.trade(product_id,'freeze', second)[0],200)
        self.assertEqual(self.trade(product_id,'success')[0],200)
        self.assertIsNone(self.request('/api/product')[1]['product'])
        self.assertEqual(self.trade(product_id,'failure')[0],409)
        result = self.request('/api/trades')[1]
        self.assertEqual({i['id']:i['status'] for i in result['intents']}, {first:'closed',second:'completed'})
        self.assertEqual(len(result['events']),4)
        self.assertEqual(self.request('/api/products')[1]['products'][0]['status'],'sold')
        new_id = self.publish(name='第二件')[1]['id']
        self.assertEqual(self.trade(new_id,'freeze', first)[0],409)

    def test_validation_and_duplicate_intent(self):
        self.login()
        for price in ('0','-1','NaN','1.001','10000000','abc'):
            self.assertEqual(self.publish(price=price)[0],400,price)
        for image in ('data:image/svg+xml;base64,AAAA','data:image/png;base64,AAAA'):
            self.assertEqual(self.publish(image=image)[0],400)
        self.assertEqual(self.publish(name=' ')[0],400)
        product_id = self.publish()[1]['id']
        self.assertEqual(self.intent(product_id)[0],200)
        self.assertEqual(self.intent(product_id, '138-0013-8000')[0],409)
        self.assertEqual(self.intent(product_id, 'abcabc')[0],400)
        self.assertEqual(self.intent(9999)[0],409)

    def test_authentication_and_password(self):
        for path in ('/api/products','/api/trades'):
            self.assertEqual(self.request(path)[0],401)
        self.assertEqual(self.publish()[0],401)
        self.assertEqual(self.trade(1,'freeze',1)[0],401)
        self.assertEqual(self.request('/api/password',{'old_password':'x','new_password':'newpass123'})[0],401)
        self.assertEqual(self.request('/api/login',{'username':'seller','password':'wrong'})[0],401)
        self.login()
        old_cookie = self.cookie
        self.login()
        self.assertEqual(self.request('/api/password',{'old_password':'wrong','new_password':'newpass123'})[0],400)
        self.assertEqual(self.request('/api/password',{'old_password':'seller12345','new_password':'short'})[0],400)
        self.assertEqual(self.request('/api/password',{'old_password':'seller12345','new_password':'newpass123'})[0],200)
        self.cookie = old_cookie
        self.assertFalse(self.request('/api/account')[1]['logged_in'])
        self.assertEqual(self.request('/api/products')[0],401)
        self.assertEqual(self.request('/api/login',{'username':'seller','password':'seller12345'})[0],401)
        self.login('newpass123')
        with database.connection() as conn:
            self.assertNotIn('newpass123', conn.execute('SELECT password_hash FROM seller').fetchone()[0])
        self.request('/api/logout', {})
        self.assertFalse(self.request('/api/account')[1]['logged_in'])

    def test_request_guards(self):
        data = {'username':'seller','password':'seller12345'}
        self.assertEqual(self.request('/api/login',data,headers={'X-Requested-With':''})[0],403)
        self.assertEqual(self.request('/api/login',data,headers={'Origin':'http://evil.example'})[0],403)
        self.assertEqual(self.request('/api/login',data,headers={'Content-Type':'text/plain'})[0],415)
        self.assertEqual(self.request('/api/intents', {'product_id':True,'buyer_name':'张','contact':'13800138000'})[0],400)
        self.assertEqual(self.request('/api/missing')[0],404)

    def test_concurrent_publish(self):
        self.login()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _: self.publish()[0], range(2)))
        self.assertEqual(sorted(results),[200,409])
        self.assertEqual(len(self.request('/api/products')[1]['products']),1)

    def test_persistence(self):
        self.login()
        product_id = self.publish()[1]['id']
        database.initialize(security.hash_password('different123'))
        self.assertEqual(self.request('/api/product')[1]['product']['id'],product_id)
        self.login()  # 再次初始化不会覆盖已经保存的密码。


if __name__ == '__main__':
    unittest.main(verbosity=2)
