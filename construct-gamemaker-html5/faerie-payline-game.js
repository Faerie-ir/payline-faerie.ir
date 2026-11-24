/**
 * Faerie Payline SDK — HTML5 Game Engines (Construct 3, GameMaker, Phaser, GDevelop, PixiJS)
 * بدون وابستگی؛ قابل فراخوانی مستقیم از Event Sheet در Construct 3 یا اکستنشن JS در GameMaker.
 */
(function (global) {
  'use strict';

  class FaerieGamePayline {
    constructor(options) {
      const opts = options || {};
      this.baseUrl = String(opts.baseUrl || 'https://faerie.ir').replace(/\/+$/, '');
      this.paylineToken = String(opts.paylineToken || '');
      this.autoConsume = opts.autoConsume !== false;
      this._pendingId = localStorage.getItem('faerie_pending_pid') || '';
      this._pendingUser = localStorage.getItem('faerie_pending_uid') || '';

      window.addEventListener('focus', () => {
        if (this._pendingId && typeof this.onAutoVerified === 'function') {
          const pid = this._pendingId;
          this._clearPending();
          this.verifyAndDeliver(pid, this._pendingUser).then(this.onAutoVerified).catch(() => {});
        }
      });
    }

    async getProducts() {
      const r = await this._call('GET', '/api/v1/iap/products');
      return (r.data && r.data.products) || [];
    }

    async buy(sku, userId, extras) {
      // اگر بازی داخل Android WebView دارای پل امن FaerieWebViewBridge اجرا شود، از پل امن نیتیو استفاده می‌کند
      if (global.FaerieNativeBridge && typeof global.FaerieNativeBridge.startPurchase === 'function') {
        const raw = global.FaerieNativeBridge.startPurchase(sku, userId, JSON.stringify(extras || {}));
        return JSON.parse(raw);
      }
      const ex = extras || {};
      const body = Object.assign({ sku, user_id: userId, return_url: global.location ? global.location.href : '' }, ex);
      const r = await this._call('POST', '/api/v1/iap/init', body);
      const purchase = (r.data && r.data.purchase) || r.data;
      const payUrl = (r.data && r.data.payment_url) || purchase.payment_url;
      this._pendingId = purchase.purchase_id;
      this._pendingUser = userId;
      try {
        localStorage.setItem('faerie_pending_pid', this._pendingId);
        localStorage.setItem('faerie_pending_uid', userId);
      } catch (_) {}
      global.open(payUrl, '_blank');
      return purchase;
    }

    async verifyAndDeliver(purchaseId, userId) {
      const r = await this._call('GET', '/api/v1/iap/status/' + encodeURIComponent(purchaseId));
      let p = r.data;
      if (!p || (p.status !== 'paid' && p.status !== 'consumed')) {
        throw new Error('پرداخت هنوز تکمیل نشده است (' + (p ? p.status : 'unknown') + ')');
      }
      if (this.autoConsume && p.status === 'paid') {
        const cr = await this._call('POST', '/api/v1/iap/consume', { purchase_id: purchaseId, user_id: userId });
        if (cr.data) p = cr.data;
      }
      this._clearPending();
      return p;
    }

    _clearPending() {
      this._pendingId = '';
      try { localStorage.removeItem('faerie_pending_pid'); } catch (_) {}
    }

    async _call(method, path, body) {
      const res = await fetch(this.baseUrl + path, {
        method,
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json',
          'X-Payline': this.paylineToken
        },
        body: body ? JSON.stringify(body) : undefined
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.message || ('خطای ارتباط با فائری: ' + res.status));
      }
      return json;
    }
  }

  global.FaerieGamePayline = FaerieGamePayline;
})(typeof window !== 'undefined' ? window : globalThis);
