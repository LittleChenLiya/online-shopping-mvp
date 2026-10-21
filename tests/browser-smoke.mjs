// 庄云月：可重复的浏览器验收；隔离临时SQLite及浏览器上下文，不使用正式数据。
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createServer} from 'node:net';
import {mkdtemp, mkdir, writeFile, rm, readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {dirname, join, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const jar = join(root, 'backend-java/target/online-shopping.jar');
const output = join(root, 'docs/java-migration/browser-results');
await mkdir(output, {recursive:true});
const temp = await mkdtemp(join(tmpdir(), 'shopping-browser-'));
const port = await new Promise((done, reject) => {
  const server = createServer(); server.on('error', reject);
  server.listen(0, '127.0.0.1', () => {const port = server.address().port; server.close(() => done(port));});
});
const java = process.env.SHOP_JAVA_HOME ? join(process.env.SHOP_JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe':'java') : 'java';
const service = spawn(java, ['-Dfile.encoding=UTF-8', '-jar', jar, `--server.port=${port}`, '--server.address=127.0.0.1', `--shop.db-path=${join(temp,'test.db')}`, '--shop.initial-password=seller12345'], {cwd:root, windowsHide:true});
const logs = []; const checks = []; const errors = []; let browser;
service.stdout.on('data', data => logs.push(data.toString())); service.stderr.on('data', data => logs.push(data.toString()));
service.on('error', error => errors.push(error.message));
const base = `http://127.0.0.1:${port}`;
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
const passed = name => { checks.push({name,status:'PASS'}); console.log(`PASS: ${name}`); };
const watch = page => page.on('pageerror', error => errors.push(error.message));
async function login(page, password='seller12345') {
  await page.getByLabel('账号', {exact:true}).fill('seller');
  await page.getByLabel('密码', {exact:true}).fill(password);
  await page.getByRole('button', {name:'登录工作台'}).click();
}
async function publish(page, name) {
  await page.getByLabel('商品名称', {exact:true}).fill(name);
  await page.getByLabel('商品描述', {exact:true}).fill('浏览器验收商品，线下交钱交货。');
  await page.getByLabel('价格（元）', {exact:true}).fill('19.90');
  await page.getByLabel('商品图片', {exact:true}).setInputFiles({name:'test.png',mimeType:'image/png',buffer:PNG});
  await page.locator('#preview').waitFor({state:'visible'});
  assert.ok(await page.locator('#preview').evaluate(image => image.complete && image.naturalWidth > 0));
  await page.getByRole('button', {name:'发布商品',exact:true}).click();
  await page.getByText('当前商品「' + name + '」尚未售出', {exact:false}).waitFor();
}
try {
  let ready = false;
  for (let i=0;i<100;i++) {
    if (service.exitCode !== null || errors.length) throw new Error(`Java startup failed: ${logs.join('')}`);
    try { if ((await fetch(base+'/api/account')).ok) {ready=true;break;} } catch {}
    await delay(200);
  }
  assert.ok(ready, 'Java service startup timeout');
  const channel = process.env.SHOP_BROWSER_CHANNEL || (process.platform === 'win32' ? 'msedge':undefined);
  browser = await chromium.launch({channel, headless:true});
  const seller = await browser.newContext({viewport:{width:1440,height:1000}});
  const page = await seller.newPage(); watch(page);
  await page.goto(base);
  await page.getByRole('heading', {name:'下一件好物，正在准备。'}).waitFor();
  passed('empty shop renders');
  await page.getByRole('link', {name:'发布与历史',exact:true}).click();
  await login(page);
  await page.getByRole('heading', {name:'发布下一件好物'}).waitFor();
  await publish(page,'浏览器验收杯');
  passed('seller login, image preview and publish');
  await page.screenshot({path:join(output,'01-published-desktop.png'),fullPage:true});
  const buyer = await browser.newContext({viewport:{width:1280,height:900}});
  const buyerPage = await buyer.newPage(); watch(buyerPage);
  await buyerPage.goto(base+'/#buy');
  await buyerPage.getByLabel('姓名', {exact:true}).fill('浏览器买家');
  await buyerPage.getByLabel('联系电话', {exact:true}).fill('13800138000');
  await buyerPage.getByLabel('备注（选填）', {exact:true}).fill('周末联系 <script>仅作为文字</script>');
  await buyerPage.getByRole('button', {name:'提交购买意向'}).click();
  await buyerPage.getByRole('heading', {name:'等待卖家与你联系'}).waitFor();
  passed('buyer submits intent without login');
  await page.getByRole('link', {name:'交易管理',exact:true}).click();
  await page.getByRole('button', {name:'选择并冻结'}).click();
  await page.getByRole('button', {name:'取消',exact:true}).click();
  assert.equal((await (await buyer.request.get(base+'/api/product')).json()).product.status,'active');
  passed('cancel confirmation leaves active state unchanged');
  await page.getByRole('button', {name:'选择并冻结'}).click();
  await page.getByRole('button', {name:'确认操作',exact:true}).click();
  await page.getByRole('button', {name:'交易失败 · 恢复上线'}).waitFor();
  await page.screenshot({path:join(output,'02-frozen-desktop.png'),fullPage:true});
  // 提交成功后仍在#buy；相同URL导航不会触发hashchange，需真实刷新旧页面。
  await buyerPage.reload();
  await buyerPage.getByRole('heading', {name:'暂时无法提交意向'}).waitFor();
  passed('freeze blocks buyer form and renders selected trade');
  await page.getByRole('button', {name:'交易失败 · 恢复上线'}).click();
  await page.getByRole('button', {name:'确认操作',exact:true}).click();
  await page.getByRole('button', {name:'选择并冻结'}).waitFor();
  assert.equal((await (await buyer.request.get(base+'/api/product')).json()).product.status,'active');
  passed('failed trade restores active state');
  await page.getByRole('button', {name:'选择并冻结'}).click();
  await page.getByRole('button', {name:'确认操作',exact:true}).click();
  await page.getByRole('button', {name:'交易成功 · 下架'}).click();
  await page.getByRole('button', {name:'确认操作',exact:true}).click();
  await page.getByText('没有待交易商品。', {exact:true}).waitFor();
  assert.equal((await (await buyer.request.get(base+'/api/product')).json()).product,null);
  await page.getByRole('link', {name:'发布与历史',exact:true}).click();
  await page.locator('.history-item').getByText('已售出',{exact:true}).waitFor();
  await publish(page,'第二件浏览器商品');
  passed('successful trade removes public item, keeps history and permits next product');
  await page.getByRole('link', {name:'卖家账号',exact:true}).click();
  await page.getByLabel('原密码',{exact:true}).fill('seller12345');
  await page.getByLabel('新密码',{exact:true}).fill('browser12345');
  await page.getByLabel('再次输入新密码',{exact:true}).fill('browser12345');
  await page.getByRole('button', {name:'修改密码',exact:true}).click();
  await page.getByRole('heading', {name:'卖家登录',exact:true}).waitFor();
  await login(page,'browser12345');
  await page.getByRole('button', {name:'退出登录',exact:true}).waitFor();
  passed('password change requires re-login and new password works');
  await page.evaluate(() => {location.hash='products'; location.hash='trades'; location.hash='shop';});
  await page.getByRole('heading', {name:'一件好物，等一个有缘人。',exact:true}).waitFor();
  await page.getByRole('heading', {name:'第二件浏览器商品',exact:true}).waitFor();
  passed('rapid route changes finish on latest page');
  await page.getByRole('link', {name:'卖家账号',exact:true}).click();
  await page.getByRole('button', {name:'退出登录',exact:true}).click();
  await page.getByRole('heading', {name:'卖家登录',exact:true}).waitFor();
  passed('logout returns to login page');
  const mobile = await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const mobilePage = await mobile.newPage(); watch(mobilePage);
  await mobilePage.goto(base);
  await mobilePage.getByRole('heading', {name:'第二件浏览器商品',exact:true}).waitFor();
  assert.ok(await mobilePage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile page has horizontal overflow');
  await mobilePage.screenshot({path:join(output,'03-mobile.png'),fullPage:true});
  passed('390px mobile product layout has no horizontal overflow');
  assert.deepEqual(errors, [], 'uncaught page errors');
  passed('no uncaught browser errors');
  await writeFile(join(output,'results.json'), JSON.stringify({time:new Date().toISOString(),executor:'Codex',browser:await browser.version(),channel,success:true,jar_sha256:createHash('sha256').update(await readFile(jar)).digest('hex'),checks,errors},null,2)+'\n');
} catch(error) {
  await writeFile(join(output,'results.json'), JSON.stringify({time:new Date().toISOString(),success:false,checks,errors,failure:error.stack},null,2)+'\n');
  throw error;
} finally {
  if (browser) await browser.close();
  service.kill();
  if (service.exitCode === null) await Promise.race([new Promise(done=>service.once('exit',done)),delay(5000)]);
  await writeFile(join(output,'service.log'),logs.join(''));
  const safe = resolve(tmpdir())+sep;
  if (!resolve(temp).startsWith(safe) || !temp.includes('shopping-browser-')) throw new Error('Unexpected temporary path');
  await rm(temp,{recursive:true,force:true});
}
