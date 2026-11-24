/**
 * @faerie/payline-node — Official Server-Side SDK for Node.js 18+, Express, Fastify, NestJS & Next.js
 */
export interface InitPurchaseInput {
  sku: string;
  userId: string;
  amountToman?: number;
  mobile?: string;
  nationalCode?: string;
  discountCode?: string;
  returnUrl?: string;
}

export interface FaeriePurchaseRecord {
  purchase_id: string;
  sku: string;
  amount_toman: number;
  gross_toman: number;
  discount_toman: number;
  discount_code?: string | null;
  status: 'pending' | 'paid' | 'consumed' | 'failed' | 'canceled' | 'refunded' | 'expired';
  ref_number?: string | null;
  payment_url: string;
  invoice_number?: string | null;
  invoice_url?: string | null;
}

export class FaeriePaylineError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number = 0,
    public readonly code: string = '',
    public readonly requiresNationalId: boolean = false
  ) {
    super(message);
    this.name = 'FaeriePaylineError';
  }
}

export class FaeriePaylineServer {
  private readonly baseUrl: string;
  private readonly token: string;

  constructor(paylineToken: string, baseUrl: string = 'https://faerie.ir') {
    this.token = paylineToken.trim();
    this.baseUrl = baseUrl.replace(/\/+$/, '');
  }

  async getProducts() {
    const data = await this.request('GET', '/api/v1/iap/products');
    return data.products ?? [];
  }

  async initPurchase(input: InitPurchaseInput): Promise<FaeriePurchaseRecord> {
    const data = await this.request('POST', '/api/v1/iap/init', {
      sku: input.sku,
      user_id: input.userId,
      amount: input.amountToman,
      mobile: input.mobile,
      national_code: input.nationalCode,
      discount_code: input.discountCode,
      return_url: input.returnUrl,
    });
    const p: FaeriePurchaseRecord = data.purchase ?? data;
    if (!p.payment_url && data.payment_url) p.payment_url = data.payment_url;
    return p;
  }

  async getStatus(purchaseId: string): Promise<FaeriePurchaseRecord> {
    return await this.request('GET', `/api/v1/iap/status/${encodeURIComponent(purchaseId)}`);
  }

  async consume(purchaseId: string, userId: string): Promise<FaeriePurchaseRecord> {
    return await this.request('POST', '/api/v1/iap/consume', {
      purchase_id: purchaseId,
      user_id: userId,
    });
  }

  async verifyAndConsume(purchaseId: string, userId: string, autoConsume = true): Promise<FaeriePurchaseRecord> {
    let tx = await this.getStatus(purchaseId);
    if (tx.status !== 'paid' && tx.status !== 'consumed') {
      throw new FaeriePaylineError(`پرداخت تکمیل نشده است (${tx.status})`, 402);
    }
    if (autoConsume && tx.status === 'paid') {
      tx = await this.consume(purchaseId, userId);
    }
    return tx;
  }

  async getInvoice(purchaseId: string) {
    return await this.request('GET', `/api/v1/iap/invoice/${encodeURIComponent(purchaseId)}`);
  }

  private async request(method: 'GET' | 'POST', path: string, body?: Record<string, unknown>): Promise<any> {
    const res = await fetch(this.baseUrl + path, {
      method,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json; charset=utf-8',
        'X-Payline': this.token,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const json: any = await res.json().catch(() => ({}));
    if (!res.ok || !json.success) {
      throw new FaeriePaylineError(
        json.message || `خطای سرور فائری (${res.status})`,
        res.status,
        json.code || '',
        Boolean(json.requires_national_id)
      );
    }
    return json.data;
  }
}
