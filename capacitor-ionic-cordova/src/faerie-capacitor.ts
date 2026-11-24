export interface CapacitorPaylineOptions {
  baseUrl?: string;
  paylineToken: string;
  returnScheme?: string; // مثلاً myapp://payline-return
}

/**
 * Faerie Payline SDK برای برنامه‌های هیبریدی Capacitor / Ionic / Cordova.
 */
export class CapacitorFaeriePayline {
  private readonly baseUrl: string;
  private readonly token: string;
  private readonly returnScheme?: string;

  constructor(options: CapacitorPaylineOptions) {
    this.baseUrl = (options.baseUrl || 'https://faerie.ir').replace(/\/+$/, '');
    this.token = options.paylineToken.trim();
    this.returnScheme = options.returnScheme;
  }

  async getProducts() {
    const r = await this.request('GET', '/api/v1/iap/products');
    return r.products ?? [];
  }

  async startAndOpen(sku: string, userId: string, extras: Record<string, unknown> = {}) {
    const r = await this.request('POST', '/api/v1/iap/init', {
      sku,
      user_id: userId,
      return_url: this.returnScheme,
      ...extras,
    });
    const tx = r.purchase ?? r;
    const payUrl = r.payment_url || tx.payment_url;
    window.open(payUrl, '_system');
    return tx;
  }

  async verifyAndConsume(purchaseId: string, userId: string, autoConsume = true) {
    let tx = await this.request('GET', `/api/v1/iap/status/${encodeURIComponent(purchaseId)}`);
    if (tx.status !== 'paid' && tx.status !== 'consumed') {
      throw new Error(`پرداخت کامل نشده است (${tx.status})`);
    }
    if (autoConsume && tx.status === 'paid') {
      tx = await this.request('POST', '/api/v1/iap/consume', { purchase_id: purchaseId, user_id: userId });
    }
    return tx;
  }

  private async request(method: string, path: string, body?: unknown) {
    const res = await fetch(this.baseUrl + path, {
      method,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Payline': this.token,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      throw new Error(json.message || 'خطای اتصال به درگاه فائری');
    }
    return json.data;
  }
}
