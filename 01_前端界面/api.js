/* 谢相逸：前端接口适配层。路径、请求头和错误处理集中维护。 */
window.ShopApi = (() => {
  async function request(path, data) {
    const options = {credentials: 'same-origin'};
    if (data !== undefined) {
      options.method = 'POST';
      options.headers = {'Content-Type': 'application/json', 'X-Requested-With': 'ShopApp'};
      options.body = JSON.stringify(data);
    }
    const response = await fetch(path, options);
    let result;
    try { result = await response.json(); }
    catch { throw new Error('服务响应格式异常，请稍后重试'); }
    if (!response.ok) throw new Error(result.error || '请求失败');
    return result;
  }
  return Object.freeze({request});
})();
