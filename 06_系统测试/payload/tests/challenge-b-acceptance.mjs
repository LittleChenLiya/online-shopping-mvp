import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, rm, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { setTimeout as delay } from 'node:timers/promises';

const output = process.env.SHOP_TEST_OUTPUT || 'docs/review-04/evidence';
await mkdir(output, { recursive: true });
const { chromium, request } = await import(process.env.SHOP_PLAYWRIGHT || 'playwright');
const work = await mkdtemp(join(tmpdir(), 'challenge-a-'));
const port = process.env.SHOP_TEST_PORT ? Number(process.env.SHOP_TEST_PORT) : await new Promise((resolve,reject)=>{
  const listener=createServer();
  listener.on('error',reject);
  listener.listen(0,'127.0.0.1',()=>{const number=listener.address().port;listener.close(()=>resolve(number));});
});
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

  const account = (username) => ({username, password:'initial12345', phone:'13800000000', default_location:'Library'});

  for (const [context,name] of [[alice,'alice'],[bob,'buyer-b']]) {
    await call(context,a+'/customer/register',account(name));
    await call(context,a+'/customer/login',account(name));
  }
  const add=(context,id,quantity=1)=>call(context,'/api/b/cart',{product_id:id,quantity});
  const checkout=(context,ids,key=crypto.randomUUID())=>call(context,'/api/b/orders',{product_ids:ids,request_key:key});
  const all=async context=>(await call(context,'/api/b/orders')).orders;
  const state=async(id,op,expected=200)=>call(seller,'/api/b/seller/orders/'+id,{action:op},expected);
  await call(guest,'/api/b/cart',undefined,401);
  await add(alice,cup,1); await add(alice,cup,1);
  assert.equal((await call(alice,'/api/b/cart')).items[0].quantity,2);
  await call(alice,'/api/b/cart',{product_id:cup,quantity:3,mode:'set'});
  assert.equal((await call(alice,'/api/b/cart')).items[0].quantity,3);
  await call(alice,'/api/b/cart',{product_id:cup,quantity:0},400);
  assert.equal((await call(bob,'/api/b/cart')).items.length,0);
  pass('B01','login, cart ownership, quantity merge/edit and invalid quantity');

  await call(alice,'/api/b/favorites',{product_id:cup});
  await call(alice,'/api/b/favorites',{product_id:cup});
  assert.equal((await call(alice,'/api/b/favorites')).items.length,1);
  await add(alice,bowl);
  await call(alice,'/api/b/cart/favorite',{product_ids:[cup,bowl]});
  assert.equal((await call(alice,'/api/b/cart')).items.length,0);
  assert.equal((await call(alice,'/api/b/favorites')).items.length,2);
  await call(alice,'/api/b/favorites/'+cup+'/remove',{});
  pass('B02','batch move removes cart items and deduplicates favorites');

  await add(alice,cup,2); await add(alice,bowl,1);
  const key=crypto.randomUUID();
  const placed=await checkout(alice,[cup,bowl],key);
  assert.equal(placed.order.items.length,2);
  assert.equal(placed.order.total_cents,5970);
  assert.equal(placed.order.status,'placed');
  assert.equal((await call(alice,'/api/b/cart')).items.length,0);
  assert.equal((await checkout(alice,[cup,bowl],key)).order.id,placed.order.id);
  await call(alice,'/api/b/orders',{product_ids:[cup],request_key:key},409);
  assert.deepEqual(inventory(await detail(guest,cup)),[5,2,3]);
  pass('B03','single multi-item order, price snapshots, total, reservation and retry safety');

  const shortage=(await publish('Shortage')).id;
  await add(alice,shortage,6); await add(alice,cup,1);
  const partial=await checkout(alice,[shortage,cup]);
  assert.equal(partial.order.items.length,1);
  assert.equal(partial.rejected[0].reason,'缺货');
  assert.equal((await call(alice,'/api/b/cart')).items[0].product_id,shortage);
  const empty=await checkout(alice,[shortage]);
  assert.equal(empty.created,false);
  assert.equal((await all(alice)).length,2);
  await call(alice,'/api/b/orders',{product_ids:[cup,cup],request_key:crypto.randomUUID()},400);
  pass('B04','insufficient line excluded, successful line ordered, shortage retained; all-shortage creates no order');

  await call(bob,'/api/b/orders/'+placed.order.id,{action:'cancel'},404);
  assert.equal((await all(bob)).length,0);
  await call(alice,'/api/b/orders/'+placed.order.id,{action:'ship'},403);
  await state(placed.order.id,'ship',409);
  await state(placed.order.id,'confirm');
  await state(placed.order.id,'ready');
  await state(placed.order.id,'ship');
  assert.deepEqual(inventory(await detail(guest,cup)),[3,1,2]);
  await call(alice,'/api/b/orders/'+placed.order.id,{action:'cancel'},409);
  await state(placed.order.id,'complete');
  await state(placed.order.id,'cancel',409);
  await state(placed.order.id,'complete',409);
  assert.deepEqual((await all(alice)).find(o=>o.id===placed.order.id).events.map(e=>e.status),
    ['placed','confirmed','ready','shipped','completed']);
  pass('B05','five states, no skipped/repeated transitions, owner isolation and cancellation deadlines');

  await call(alice,'/api/b/orders/'+partial.order.id,{action:'cancel'});
  assert.deepEqual(inventory(await detail(guest,cup)),[3,0,3]);
  const cancelId=(await checkout(bob,[(await add(bob,cup),cup)])).order.id;
  await state(cancelId,'confirm'); await state(cancelId,'ready'); await state(cancelId,'ship');
  assert.equal((await detail(guest,cup)).stock,2);
  await state(cancelId,'cancel');
  assert.deepEqual(inventory(await detail(guest,cup)),[3,0,3]);
  await state(cancelId,'cancel',409);
  pass('B06','customer pre-shipping cancellation and seller post-shipping cancellation restore inventory once');

  const last=(await publish('Last-stock')).id;
  await call(seller,a+'/seller/products/'+last+'/stock',{stock:1,listed:true});
  await add(alice,last); await add(bob,last);
  const concurrent=await Promise.all([checkout(alice,[last]),checkout(bob,[last])]);
  assert.equal(concurrent.filter(x=>x.created).length,1);
  assert.deepEqual(inventory(await detail(guest,last)),[1,1,0]);
  await call(seller,a+'/seller/products/'+last+'/stock',{stock:0},409);
  pass('B07','concurrent checkouts reserve final unit once; seller cannot lower stock below reservation');

  // A/B share the same reservation calculation.
  const mixed=(await publish('Shared-stock')).id;
  await call(seller,a+'/seller/products/'+mixed+'/stock',{stock:1,listed:true});
  const oldOrder=await order(alice,mixed,1);
  await trade(seller,oldOrder.id,'freeze');
  await add(bob,mixed);
  assert.equal((await checkout(bob,[mixed])).created,false);
  pass('B08','A and B orders share available inventory without overselling');

  await call(alice,a+'/customer/profile',{phone:'13900000000',default_location:'Gate'});
  const old=(await all(alice)).find(o=>o.id===placed.order.id);
  assert.equal(old.phone,'13800000000');
  assert.equal(old.trade_location,'Library');
  assert.equal(old.items[0].unit_price_cents,1990);
  await stop(); await start();
  await call(alice,a+'/customer/login',account('alice'));
  assert.equal((await all(alice)).find(o=>o.id===placed.order.id).status,'completed');
  assert.ok((await call(alice,'/api/b/favorites')).items.length);
  assert.ok((await call(alice,'/api/b/cart')).items.length);
  assert.deepEqual(inventory(await detail(guest,last)),[1,1,0]);
  pass('B09','restart preserves orders, events, cart, favorites and reservation; profile edits preserve snapshots');


  const directory=await call(seller,'/api/b/seller/customers');
  const aliceRow=directory.customers.find(c=>c.username==='alice');
  assert.ok(aliceRow.order_count>=2);
  assert.equal(aliceRow.completed_total_cents,5970);
  const filtered=await call(seller,'/api/b/seller/orders?customer_id='+aliceRow.id);
  assert.ok(filtered.orders.every(o=>o.username==='alice'));
  await call(guest,'/api/b/seller/orders',undefined,401);
  await call(alice,'/api/b/seller/orders',undefined,401);
  pass('B10','seller directory statistics, customer filter and seller authorization');

  // Cancellation boundaries are checked separately at each pre-shipping state.
  for (const stage of ['placed','confirmed','ready']) {
    await add(alice,cup);
    const id=(await checkout(alice,[cup])).order.id;
    if(stage!=='placed')await state(id,'confirm');
    if(stage==='ready')await state(id,'ready');
    await call(alice,'/api/b/orders/'+id,{action:'cancel'});
    await call(alice,'/api/b/orders/'+id,{action:'cancel'},409);
    assert.deepEqual(inventory(await detail(guest,cup)),[3,0,3]);
  }
  // An invalid batch must roll back valid earlier entries as well.
  const cartBefore=(await call(alice,'/api/b/cart')).items.length;
  await call(alice,'/api/b/cart/favorite',{product_ids:[shortage,999999]},400);
  assert.equal((await call(alice,'/api/b/cart')).items.length,cartBefore);
  const winner=concurrent.find(x=>x.created).order.id;
  await state(winner,'confirm');await state(winner,'ready');await state(winner,'ship');
  await call(guest,a+'/products/'+last,undefined,404);
  await state(winner,'cancel');
  assert.deepEqual(inventory(await detail(guest,last)),[1,0,1]);
  pass('B11','cancellation at placed/confirmed/ready; batch rollback; sellout delisting and cancellation restock');

  browser=await chromium.launch({channel:process.env.SHOP_BROWSER||'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(baseURL+'/challenge-b#customer');
  await page.locator('#customer-login [name=username]').fill('alice');
  await page.locator('#customer-login [name=password]').fill('initial12345');
  await page.getByRole('button',{name:'登录客户账号',exact:true}).click();
  await page.locator('.catalog-grid').waitFor();
  await page.goto(baseURL+'/challenge-b#product/'+cup);
  await page.locator('#add-cart').waitFor();
  await page.locator('#add-cart [name=quantity]').fill('1');
  await page.getByRole('button',{name:'加入购物车',exact:true}).click();
  await page.getByRole('status').filter({hasText:'购物车已更新'}).waitFor();
  await page.getByRole('link',{name:'购物车',exact:true}).click();
  await page.locator('#checkout').waitFor();
  await page.getByRole('checkbox',{name:'选择Ceramic cup',exact:true}).check();
  await page.locator('#checkout').click();
  await page.getByRole('button',{name:'确认操作',exact:true}).click();
  await page.locator('#checkout-result').getByText('下单成功',{exact:false}).waitFor();
  await page.screenshot({path:output+'/01-cart-checkout.png',fullPage:true});
  await page.getByRole('link',{name:'查看订单商品清单',exact:true}).click();
  await page.locator('details').first().waitFor();
  await page.screenshot({path:output+'/02-order-items.png',fullPage:true});
  await page.getByRole('link',{name:'卖家后台',exact:true}).click();
  await page.locator('#seller-login [name=password]').fill('seller12345');
  await page.getByRole('button',{name:'登录卖家后台',exact:true}).click();
  await page.locator('#publish-a').waitFor();
  await page.getByRole('link',{name:'订单管理',exact:true}).click();
  await page.locator('[data-op=confirm]').first().waitFor();
  await page.screenshot({path:output+'/03-seller-orders.png',fullPage:true});
  assert.deepEqual(errors,[]);
  pass('B12','browser login, add-to-cart, selected checkout, order details and seller management; no page errors');
  console.log(passed+'/12 passed');

} finally {
  await browser?.close();
  await Promise.all(contexts.map(context => context.dispose()));
  await stop();
  await rm(work, { recursive: true, force: true });
}

