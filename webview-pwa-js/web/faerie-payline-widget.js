/**
 * Faerie Payline Web & PWA SDK + <faerie-pay-button> Custom Element
 * قابل استفاده در هر وب‌سایت، PWA، React، Vue، Angular یا صفحهٔ داخل Android WebView.
 */
(function (global) {
  'use strict';

  class FaeriePaylineWeb {
    constructor(config) {
      const cfg = config || {};
      this.baseUrl = String(cfg.baseUrl || 'https://faerie.ir').replace(/\/+$/, '');
      this.paylineToken = String(cfg.paylineToken || '');
      this.initEndpoint = cfg.initEndpoint || ''; // اختیاری: آدرس بک‌اند خودتان برای مخفی ماندن توکن در وب عمومی
      this.statusEndpoint = cfg.statusEndpoint || '';
    }

    hasNativeAndroidBridge() {
      return Boolean(global.FaerieNativeBridge && typeof global.FaerieNativeBridge.startPurchase === 'function');
    }

    async getProducts() {
      const r = await this._fetch('GET', '/api/v1/iap/products');
      return (r.data && r.data.products) || [];
    }

    async startPurchase(params) {
      const p = params || {};
      // ۱) اگر صفحه داخل Android WebView مجهز به FaerieWebViewBridge باز شده باشد، از پل امن نیتیو استفاده کن
      if (this.hasNativeAndroidBridge()) {
        const raw = global.FaerieNativeBridge.startPurchase(
          String(p.sku || ''),
          String(p.userId || 'guest'),
          JSON.stringify({
            mobile: p.mobile || '',
            national_code: p.nationalCode || '',
            discount_code: p.discountCode || '',
            amount: p.amountToman || 0
          })
        );
        return JSON.parse(raw);
      }

      // ۲) اگر توسعه‌دهنده بک‌اند واسط معرفی کرده باشد (بهترین روش امنیتی برای وب عمومی)
      const returnUrl = p.returnUrl || (global.location ? global.location.href.split('#')[0] : '');
      if (this.initEndpoint) {
        const res = await fetch(this.initEndpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
          body: JSON.stringify(Object.assign({}, p, { return_url: returnUrl }))
        });
        const j = await res.json();
        if (!res.ok || !j.success) throw new Error(j.message || 'خطا در شروع تراکنش');
        const payUrl = (j.data && j.data.payment_url) || (j.data && j.data.purchase && j.data.purchase.payment_url);
        if (payUrl) global.location.href = payUrl;
        return j.data;
      }

      // ۳) اتصال مستقیم به API فائری با ارسال return_url برای بازگشت خودکار به همین صفحهٔ وب
      const r = await this._fetch('POST', '/api/v1/iap/init', {
        sku: p.sku,
        user_id: p.userId || 'web_user',
        mobile: p.mobile,
        national_code: p.nationalCode,
        discount_code: p.discountCode,
        amount: p.amountToman,
        return_url: returnUrl
      });
      const tx = (r.data && r.data.purchase) || r.data;
      const payUrl = (r.data && r.data.payment_url) || tx.payment_url;
      try {
        sessionStorage.setItem('faerie_last_purchase_id', tx.purchase_id);
        sessionStorage.setItem('faerie_last_user_id', String(p.userId || 'web_user'));
      } catch (_) {}
      global.location.href = payUrl;
      return tx;
    }

    async verifyFromUrlOrStorage(autoConsume = true) {
      const url = new URL(global.location.href);
      const pid = url.searchParams.get('purchase_id') || sessionStorage.getItem('faerie_last_purchase_id');
      if (!pid) return null;
      const uid = sessionStorage.getItem('faerie_last_user_id') || 'web_user';

      if (this.statusEndpoint) {
        const res = await fetch(this.statusEndpoint + '?purchase_id=' + encodeURIComponent(pid));
        const j = await res.json();
        return j.data || j;
      }

      const r = await this._fetch('GET', '/api/v1/iap/status/' + encodeURIComponent(pid));
      let tx = r.data;
      if (tx && tx.status === 'paid' && autoConsume) {
        const cr = await this._fetch('POST', '/api/v1/iap/consume', { purchase_id: pid, user_id: uid });
        if (cr.data) tx = cr.data;
      }
      try { sessionStorage.removeItem('faerie_last_purchase_id'); } catch (_) {}
      return tx;
    }

    async _fetch(method, path, body) {
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
        const err = new Error(json.message || ('خطای درگاه فائری (' + res.status + ')'));
        err.requiresNationalId = Boolean(json.requires_national_id);
        throw err;
      }
      return json;
    }
  }

  // تعریف تگ HTML آماده: <faerie-pay-button sku="vip" user-id="u1" token="pl_..." label="پرداخت با فائری"></faerie-pay-button>
  if (typeof customElements !== 'undefined' && !customElements.get('faerie-pay-button')) {
    class FaeriePayButtonElement extends HTMLElement {
      connectedCallback() {
        const label = this.getAttribute('label') || 'پرداخت با فائری';
        const root = this.attachShadow({ mode: 'open' });
        root.innerHTML = `
          <style>
            button {
              direction: rtl;
              font-family: Vazirmatn, Tahoma, sans-serif;
              display: inline-flex;
              align-items: center;
              gap: 10px;
              background: linear-gradient(135deg, #5B5CE6, #7C4DFF);
              color: #fff;
              border: none;
              border-radius: 14px;
              padding: 12px 22px;
              font-size: 14px;
              font-weight: 700;
              cursor: pointer;
              box-shadow: 0 6px 18px rgba(91, 92, 230, 0.28);
              transition: transform .15s ease, opacity .15s ease;
            }
            button:hover { transform: translateY(-1px); }
            button:disabled { opacity: .65; cursor: wait; }
            .badge {
              width: 24px; height: 24px; border-radius: 50%;
              background: rgba(255,255,255,.22);
              display: inline-flex; align-items: center; justify-content: center;
              font-weight: 900; font-size: 13px;
            }
          </style>
          <button type="button"><span class="badge">F</span><span class="txt"></span></button>
        `;
        const btn = root.querySelector('button');
        const txt = root.querySelector('.txt');
        txt.textContent = label;

        btn.addEventListener('click', async () => {
          btn.disabled = true;
          txt.textContent = 'در حال اتصال به فائری…';
          try {
            const sdk = new FaeriePaylineWeb({
              baseUrl: this.getAttribute('base-url') || 'https://faerie.ir',
              paylineToken: this.getAttribute('token') || '',
              initEndpoint: this.getAttribute('init-endpoint') || ''
            });
            await sdk.startPurchase({
              sku: this.getAttribute('sku') || '',
              userId: this.getAttribute('user-id') || 'web_user',
              mobile: this.getAttribute('mobile') || '',
              nationalCode: this.getAttribute('national-code') || '',
              discountCode: this.getAttribute('discount-code') || ''
            });
          } catch (e) {
            this.dispatchEvent(new CustomEvent('faerie-error', { detail: { message: e.message }, bubbles: true }));
            alert(e.message);
          } finally {
            btn.disabled = false;
            txt.textContent = label;
          }
        });
      }
    }
    customElements.define('faerie-pay-button', FaeriePayButtonElement);
  }

  global.FaeriePaylineWeb = FaeriePaylineWeb;
})(typeof window !== 'undefined' ? window : globalThis);
