/* 成员一：页面渲染与 API 对接；业务状态由后端判定。 */
const app = document.querySelector('#app');
const labels = {active:'在售',frozen:'交易中 · 已冻结',sold:'已售出',pending:'待联系',selected:'交易中',completed:'已成交',closed:'已结束'};
let renderVersion = 0;
let toastTimer;
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money = cents => (cents / 100).toFixed(2);
const badge = status => `<span class="badge ${esc(status)}">${esc(labels[status] || status)}</span>`;

async function api(path, data) {
  const options = data === undefined ? {} : {method:'POST',headers:{'Content-Type':'application/json','X-Requested-With':'ShopApp'},body:JSON.stringify(data)};
  const response = await fetch(path, options);
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || '请求失败');
  return result;
}
function toast(message, error=false) {
  const box = document.querySelector('#toast');
  box.textContent = message; box.className = error ? 'error' : ''; box.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => {box.hidden=true;}, 5000);
}
function askConfirm(message) {
  return new Promise(resolve => {
    const dialog = document.createElement('dialog');
    dialog.className = 'confirm-dialog';
    dialog.innerHTML = `<h2>确认交易操作</h2><p>${esc(message)}</p><form method="dialog" class="actions"><button value="cancel" class="secondary">取消</button><button value="confirm">确认操作</button></form>`;
    dialog.addEventListener('close', () => { const accepted = dialog.returnValue === 'confirm'; dialog.remove(); resolve(accepted); }, {once:true});
    document.body.append(dialog);
    dialog.showModal();
  });
}
function intro(title, detail) { return `<div class="intro"><p class="eyebrow">一件小店 / 基线版</p><h1>${title}</h1><p class="muted">${detail}</p></div>`; }
function productCard(p, action='') {
  return `<section class="panel product"><img src="${esc(p.image)}" alt="${esc(p.name)}"><div>${badge(p.status)}<h1>${esc(p.name)}</h1><p class="description">${esc(p.description)}</p><div class="price">¥ ${money(p.price_cents)}</div><p class="muted">只有一件，卖掉后再制作下一件。</p>${action}<div class="steps"><span>01 查看好物</span><span>02 留下意向</span><span>03 线下交易</span></div></div></section>`;
}
function attachForm(id, handler) {
  document.querySelector(id).addEventListener('submit', async event => {
    event.preventDefault(); const form = event.currentTarget;
    const button = form.querySelector('button[type="submit"]'); button.disabled = true;
    try { await handler(Object.fromEntries(new FormData(form)), form); }
    catch (error) { toast(error.message, true); }
    finally { if (button.isConnected) button.disabled = false; }
  });
}
function loginPage() {
  return intro('卖家工作台', '登录后管理商品和线下交易。') + `<section class="panel account-panel"><h2>卖家登录</h2><form id="login"><label>账号<input name="username" value="seller" required autocomplete="username" maxlength="40"></label><label>密码<input name="password" type="password" required autocomplete="current-password" maxlength="128"></label><button type="submit">登录工作台</button></form><p class="help">本系统仅有一个卖家账号，不提供注册。</p></section>`;
}
function bindLogin() { attachForm('#login', async data => {await api('/api/login',data);toast('登录成功');await render();}); }
async function shop(version) {
  const {product:p} = await api('/api/product'); if (version !== renderVersion) return;
  app.innerHTML = intro('一件好物，等一个有缘人。', '慢慢制作，认真交易。喜欢就留下联系方式，与卖家约定线下交货。') + (p ? productCard(p,p.status==='active' ? '<a class="button" href="#buy">我想购买 →</a>' : '<button disabled>交易处理中，暂不可购买</button>') : '<section class="panel empty"><div class="empty-mark">○</div><h2>下一件好物，正在准备。</h2><p class="muted">当前没有在售商品，稍后再来看看。</p></section>');
}
async function buy(version) {
  const {product:p} = await api('/api/product'); if (version !== renderVersion) return;
  if (!p || p.status !== 'active') { app.innerHTML = intro('暂时无法提交意向', '商品已冻结或下架，请返回查看最新状态。')+'<a class="button" href="#shop">返回小店</a>'; return; }
  app.innerHTML = intro('留下购买意向', '无需注册。提交后等待卖家联系，在线下完成付款和交货。')+`<div class="grid"><section class="panel"><h2>${esc(p.name)}</h2><img class="preview" src="${esc(p.image)}" alt="商品图片"><p class="description">${esc(p.description)}</p><p class="price">¥ ${money(p.price_cents)}</p><div class="notice">提交意向不代表成交。卖家选定交易对象后才会冻结商品。</div></section><section class="panel"><h2>如何联系你</h2><form id="intent"><label>姓名<input name="buyer_name" required maxlength="40" autocomplete="name"></label><label>联系电话<input name="contact" required minlength="6" maxlength="30" type="tel" autocomplete="tel"></label><label>备注（选填）<textarea name="note" maxlength="300" placeholder="例如：方便联系的时间、交货地点建议"></textarea></label><p class="help">联系方式仅供卖家处理购买意向，不公开展示。</p><button type="submit">提交购买意向</button></form></section></div>`;
  attachForm('#intent', async data => {
    const result = await api('/api/intents',{...data,product_id:p.id});
    if (version !== renderVersion) return;
    app.innerHTML = `<section class="panel success"><p class="eyebrow">已收到你的意向</p><h1>等待卖家与你联系</h1><p>${esc(result.message)}</p><p class="muted">意向编号：${result.id}</p><a class="button" href="#shop">返回小店</a></section>`;
  });
}
async function readImage(file) {
  if (!file || !file.size) throw new Error('请上传商品图片');
  if (!['image/png','image/jpeg','image/webp'].includes(file.type) || file.size > 2*1024*1024) throw new Error('图片仅支持 PNG、JPEG、WebP，最大 2 MB');
  return new Promise((resolve,reject) => {const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(new Error('读取图片失败'));reader.readAsDataURL(file);});
}
async function productAdmin(version) {
  const {products} = await api('/api/products'); if (version !== renderVersion) return;
  const current = products.find(p => p.status !== 'sold');
  const history = products.map(p => `<div class="history-item"><img src="${esc(p.image)}" alt="${esc(p.name)}"><div class="grow wrap"><h3>${esc(p.name)}</h3><p class="muted">#${p.id} · ¥ ${money(p.price_cents)} · 发布：${esc(p.created_at)}</p>${p.sold_at?`<p class="help">成交：${esc(p.sold_at)}</p>`:''}</div>${badge(p.status)}</div>`).join('');
  app.innerHTML = intro('发布与商品历史', '一件售出，再制作下一件。在售和冻结商品都会占用唯一名额。')+`<div class="grid"><section class="panel"><h2>发布下一件好物</h2>${current?`<div class="notice">当前商品「${esc(current.name)}」尚未售出，请先在交易管理中完成交易。</div><a class="button" href="#trades">前往交易管理</a>`:`<form id="publish"><label>商品名称<input name="name" required maxlength="80"></label><label>商品描述<textarea name="description" required maxlength="2000"></textarea></label><label>价格（元）<input name="price" type="number" min="0.01" max="9999999.99" step="0.01" required></label><label>商品图片<input id="image-file" name="image" type="file" required accept="image/png,image/jpeg,image/webp"></label><p class="help">PNG / JPEG / WebP，最大 2 MB。</p><img id="preview" class="preview" alt="上传图片预览" hidden><button type="submit">发布商品</button></form>`}</section><section class="panel"><h2>全部商品记录 · ${products.length}</h2>${history||'<p class="muted">还没有发布过商品。</p>'}</section></div>`;
  if (!current) {
    document.querySelector('#image-file').addEventListener('change',async event=>{try{const src=await readImage(event.target.files[0]);const img=document.querySelector('#preview');img.src=src;img.hidden=false;}catch(e){document.querySelector('#preview').hidden=true;toast(e.message,true);}});
    attachForm('#publish',async data=>{data.image=await readImage(data.image);const result=await api('/api/products',data);toast(result.message);await render();});
  }
}
async function tradeAdmin(version) {
  const {product:p,intents,events} = await api('/api/trades'); if (version !== renderVersion) return;
  const rows = intents.map(i=>`<tr><td>#${i.id}<br>${esc(i.product_name)}</td><td>${esc(i.buyer_name)}<br>${esc(i.contact)}</td><td class="note">${esc(i.note)||'—'}</td><td>${badge(i.status)}<br><span class="muted">${esc(i.created_at)}</span></td><td>${p && p.status==='active' && i.product_id===p.id && i.status==='pending'?`<button class="small" data-action="freeze" data-intent="${i.id}">选择并冻结</button>`:'—'}</td></tr>`).join('');
  const eventRows=events.map(e=>`<tr><td>${esc(e.created_at)}</td><td>${esc(e.product_name)}</td><td>${esc(e.buyer_name)}</td><td>${{freeze:'选择买家，冻结商品',success:'线下成交，商品下架',failure:'交易失败，恢复上线'}[e.action]}</td></tr>`).join('');
  app.innerHTML=intro('交易与冻结管理','与买家联系后先冻结商品，再在线下交钱交货，最后记录交易结果。')+`<section class="panel"><h2>当前交易</h2>${p?`<h3>${esc(p.name)} · ¥ ${money(p.price_cents)} ${badge(p.status)}</h3>${p.status==='frozen'?'<div class="notice">商品已停止接收新意向。请在线下交易结束后选择真实结果。</div><div class="actions"><button data-action="success">交易成功 · 下架</button><button class="secondary" data-action="failure">交易失败 · 恢复上线</button></div>':'<p class="muted">从下方选择一位意向买家，即可冻结商品。</p>'}`:'<p class="muted">没有待交易商品。</p><a class="button" href="#products">发布商品</a>'}</section><section class="panel"><h2>购买意向 · ${intents.length}</h2><div class="table-wrap"><table><thead><tr><th>意向 / 商品</th><th>姓名 / 电话</th><th>备注</th><th>状态 / 时间</th><th>操作</th></tr></thead><tbody>${rows||'<tr><td colspan="5">暂无购买意向。</td></tr>'}</tbody></table></div></section><section class="panel"><h2>交易过程记录</h2><div class="table-wrap"><table><thead><tr><th>时间</th><th>商品</th><th>交易对象</th><th>操作</th></tr></thead><tbody>${eventRows||'<tr><td colspan="4">暂无交易记录。</td></tr>'}</tbody></table></div></section>`;
  app.querySelectorAll('[data-action]').forEach(button=>button.addEventListener('click',async()=>{
    const action=button.dataset.action;
    const question={freeze:'确认选择此买家并冻结商品？冻结后停止接收新意向。',success:'确认已线下交钱交货？确认后商品将永久标记为已售出。',failure:'确认本次交易失败并恢复商品上线？'}[action];
    if(!await askConfirm(question))return;
    button.disabled=true;
    try{const result=await api('/api/trade',{product_id:p.id,action,intent_id:Number(button.dataset.intent)});toast(result.message);await render();}catch(e){toast(e.message,true);button.disabled=false;}
  }));
}
function accountPage() {
  app.innerHTML=intro('卖家账号','单一卖家，不开放注册。修改密码后，所有已登录会话均会失效。')+`<section class="panel account-panel"><h2>修改密码</h2><p class="muted">当前账号：seller</p><form id="password"><label>原密码<input name="old_password" type="password" required maxlength="128" autocomplete="current-password"></label><label>新密码<input name="new_password" type="password" required minlength="8" maxlength="128" autocomplete="new-password"></label><label>再次输入新密码<input name="confirmation" type="password" required minlength="8" maxlength="128" autocomplete="new-password"></label><button type="submit">修改密码</button></form><div class="actions"><button id="logout" class="secondary">退出登录</button></div></section>`;
  attachForm('#password',async data=>{if(data.new_password!==data.confirmation)throw new Error('两次新密码不一致');const result=await api('/api/password',data);toast(result.message);await render();});
  document.querySelector('#logout').addEventListener('click',async()=>{try{await api('/api/logout',{});toast('已退出登录');await render();}catch(e){toast(e.message,true);}});
}
async function render() {
  const version=++renderVersion;
  const page=location.hash.slice(1)||'shop';
  app.setAttribute('aria-busy','true');
  document.querySelectorAll('nav a').forEach(a=>a.classList.toggle('active',a.hash==='#'+(page==='buy'?'shop':page)));
  try{
    if(['products','trades','account'].includes(page)){
      const account=await api('/api/account');if(version!==renderVersion)return;
      if(!account.logged_in){app.innerHTML=loginPage();bindLogin();return;}
    }
    if(page==='products')await productAdmin(version);
    else if(page==='trades')await tradeAdmin(version);
    else if(page==='account')accountPage();
    else if(page==='buy')await buy(version);
    else await shop(version);
  }catch(e){if(version===renderVersion)app.innerHTML=`<section class="panel"><h2>页面暂时无法加载</h2><p class="error">${esc(e.message)}</p><button id="retry">重新加载</button></section>`;document.querySelector('#retry')?.addEventListener('click',render);}
  finally{if(version===renderVersion)app.setAttribute('aria-busy','false');}
}
window.addEventListener('hashchange',render);
render();
