import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { once } from 'node:events';
import { setTimeout as delay } from 'node:timers/promises';

const { chromium, request } = await import(process.env.SHOP_PLAYWRIGHT || 'playwright');
const work = await mkdtemp(join(tmpdir(), 'challenge-a-'));
const port = Number(process.env.SHOP_TEST_PORT || 18754);
const baseURL = 'http://127.0.0.1:' + port;
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const options = { baseURL, timeout: 10000, extraHTTPHeaders: { 'X-Requested-With': 'ShopApp' } };
const contexts = [];
let service, browser, log = '', passed = 0;

async function client() {
  const context = await request.newContext(options);
  contexts.push(context);
  return context;
}
async function call(context, url, data, expected = 200) {
  const response = data === undefined ? await context.get(url) : await context.post(url, { data });
  const body = await response.text();
  assert.equal(response.status(), expected, url + ': ' + body);
  return JSON.parse(body);
}
async function start() {
  log = '';
  service = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', 'backend-java/target/online-shopping.jar',
    '--server.port=' + port, '--server.address=127.0.0.1',
    '--shop.db-path=' + join(work, 'acceptance.db'), '--shop.initial-password=seller12345']);
  service.stdout.on('data', chunk => { log += chunk; });
  service.stderr.on('data', chunk => { log += chunk; });
  service.on('error', error => { log += error.message; });
  for (let i = 0; i < 150; i++) {
    if (service.exitCode !== null) break;
    if (log.includes('Started ShopApplication')) return;
    await delay(200);
  }
  throw new Error('Test server did not start: ' + log);
}
async function stop() {
  if (service?.pid && service.exitCode === null && service.signalCode === null) {
    const done = once(service, 'exit');
    service.kill();
    await done;
  }
}
function pass(id, result) {
  passed++;
  console.log(id + ' PASS ' + result);
}
const a = '/api/a';
const image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jSioAAAAASUVORK5CYII=';
const order = (context, product, quantity) => call(context, a + '/orders', { product_id: product, quantity });
const history = context => call(context, a + '/customer/orders');
const detail = async (context, id) => (await call(context, a + '/products/' + id)).product;
const inventory = p => [p.stock, p.reserved, p.available];
const trade = (seller, id, action, status) => call(seller, a + '/seller/orders/' + id, { action }, status);

try {
  await start();
  const guest = await client(), seller = await client(), alice = await client(), bob = await client();
  await call(seller, '/api/login', { username: 'seller', password: 'seller12345' });
  const category = data => call(seller, a + '/seller/categories', data);
  const parent = (await category({ name: 'Workshop' })).id;
  const leaf = (await category({ name: 'Ceramics', parent_id: parent })).id;
  const description = '## Details\n**Handmade**\n- Local pickup\n![Detail](image:2)\n<script>window.challengeInjected = true</script>';
  const publish = name => call(seller, a + '/seller/products', {
    name, description, price: '19.90', category_id: leaf, stock: 5, images: [image, image]
  });
  const cup = (await publish('Ceramic cup')).id;
  const bowl = (await publish('Ceramic bowl')).id;
  const first = await call(guest, a + '/products?size=1&page=1');
  const second = await call(guest, a + '/products?size=1&page=2');
  assert.equal(first.total, 2);
  assert.deepEqual(new Set([first.products[0].id, second.products[0].id]), new Set([cup, bowl]));
  assert.deepEqual((await call(guest, a + '/products?q=Handmade')).products.length, 2);
  assert.deepEqual((await call(guest, a + '/products?q=cup')).products.map(p => p.id), [cup]);
  pass('A01', 'two listed products, pagination, name and description search');

  await call(seller, a + '/seller/categories', { name: 'Invalid', parent_id: leaf }, 400);
  assert.equal((await call(guest, a + '/products?category=' + parent)).total, 2);
  pass('A02', 'two levels, third level rejected, parent filter includes descendants');

  browser = await chromium.launch({ channel: process.env.SHOP_BROWSER || 'msedge' });
  console.log('Browser ' + browser.version());
  const page = await browser.newPage();
  await page.goto(baseURL + '/challenge-a#product/' + cup);
  await page.locator('.rich h2').waitFor();
  assert.equal(await page.locator('.rich strong').textContent(), 'Handmade');
  assert.equal(await page.locator('.rich .bullet').count(), 1);
  assert.equal(await page.locator('.rich img').count(), 1);
  assert.equal(await page.locator('.rich script').count(), 0);
  assert.equal(await page.evaluate(() => window.challengeInjected), undefined);
  await page.getByRole('button', { name: '查看第2张图片' }).click();
  assert.equal(await page.locator('.thumbs button.selected').getAttribute('data-image'), '1');
  assert.equal(await page.locator('#buy').count(), 0);
  pass('A03', 'browser formats description, switches images, escapes injected script');

  const credentials = username => ({ username, password: 'initial12345' });
  const register = async (context, username) => {
    const account = { ...credentials(username), phone: '13800000000', default_location: 'Library' };
    const result = await call(context, a + '/customer/register', account);
    await call(context, a + '/customer/login', credentials(username));
    return result.id;
  };
  await call(guest, a + '/orders', { product_id: cup, quantity: 1 }, 401);
  await call(guest, a + '/customer/register', credentials('invalid'), 400);
  const aliceId = await register(alice, 'alice');
  await register(bob, 'buyer-b');
  const firstOrder = (await order(alice, cup, 2)).id;
  assert.deepEqual(inventory(await detail(guest, cup)), [5, 0, 5]);
  await trade(seller, firstOrder, 'freeze');
  assert.deepEqual(inventory(await detail(guest, cup)), [5, 2, 3]);
  pass('A04', 'guest blocked; inventory 5/0/5 after intent and 5/2/3 after freeze');

  const candidates = [(await order(alice, cup, 3)).id, (await order(bob, cup, 3)).id];
  const outcomes = await Promise.all(candidates.map(id => seller.post(a + '/seller/orders/' + id, { data: { action: 'freeze' } })));
  assert.deepEqual(outcomes.map(r => r.status()).sort(), [200, 409]);
  const winner = candidates[outcomes.findIndex(r => r.ok())];
  assert.deepEqual(inventory(await detail(guest, cup)), [5, 5, 0]);
  pass('A05', 'concurrent freeze returns 200 and 409, available stock stays zero');

  await trade(seller, winner, 'failure');
  assert.deepEqual(inventory(await detail(guest, cup)), [5, 2, 3]);
  await call(alice, a + '/customer/orders/' + firstOrder, { action: 'cancel' });
  assert.deepEqual(inventory(await detail(guest, cup)), [5, 0, 5]);
  const sale = (await order(alice, cup, 5)).id;
  await trade(seller, sale, 'freeze');
  await trade(seller, sale, 'success');
  await trade(seller, sale, 'success', 409);
  await call(guest, a + '/products/' + cup, undefined, 404);
  assert.equal((await call(guest, a + '/products')).total, 1);
  pass('A06', 'failure and cancellation release reservations; sale delists; duplicate sale rejected');

  await call(alice, a + '/customer/profile', { phone: '13900000000', default_location: 'Gate' });
  assert.equal((await call(alice, a + '/customer/account')).customer.default_location, 'Gate');
  await call(alice, a + '/customer/password', { old_password: 'initial12345', new_password: 'changed12345' });
  await call(alice, a + '/customer/login', credentials('alice'), 401);
  await call(alice, a + '/customer/login', { username: 'alice', password: 'changed12345' });
  await call(alice, a + '/customer/logout', {});
  await call(alice, a + '/customer/orders', undefined, 401);
  await call(alice, a + '/customer/login', { username: 'alice', password: 'changed12345' });
  pass('A07', 'registration validation, profile, password rotation and logout');

  await call(bob, a + '/customer/orders/' + sale, { action: 'cancel' }, 404);
  assert.ok(!(await history(bob)).orders.some(o => o.id === sale));
  assert.ok((await history(alice)).orders.some(o => o.id === sale));
  pass('A08', 'customer history scoped to owner and cross-account cancellation denied');

  const sellerHistory = await call(seller, a + '/seller/orders?customer_id=' + aliceId);
  const snapshot = sellerHistory.orders.find(o => o.id === sale);
  assert.deepEqual([snapshot.product_name, snapshot.unit_price_cents, snapshot.phone, snapshot.trade_location],
    ['Ceramic cup', 1990, '13800000000', 'Library']);
  assert.ok((await call(seller, a + '/seller/customers')).customers.some(c => c.id === aliceId));
  pass('A09', 'seller customer lookup and unchanged order snapshots after profile edit');

  const active = (await order(alice, bowl, 2)).id;
  await trade(seller, active, 'freeze');
  await stop();
  await start();
  await call(alice, a + '/customer/login', { username: 'alice', password: 'changed12345' });
  assert.deepEqual(inventory(await detail(guest, bowl)), [5, 2, 3]);
  assert.equal((await call(guest, a + '/categories')).categories.length, 2);
  assert.equal((await history(alice)).orders.find(o => o.id === active).status, 'frozen');
  pass('A10', 'restart preserves categories, login, frozen order and 5/2/3 inventory');
  console.log(passed + '/10 passed');
} finally {
  await browser?.close();
  await Promise.all(contexts.map(context => context.dispose()));
  await stop();
  await rm(work, { recursive: true, force: true });
}
