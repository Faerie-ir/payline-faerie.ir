import React, { useEffect, useRef, useState } from 'react';
import { AppState, Linking, StyleSheet, Text, TouchableOpacity, View } from 'react-native';

export interface FaerieProduct {
  sku: string;
  title: string;
  description: string;
  type: 'consumable' | 'nonconsumable';
  price_toman: number;
  custom_amount: boolean;
  min_toman: number;
  max_toman: number;
}

export interface FaeriePurchase {
  purchase_id: string;
  sku: string;
  amount_toman: number;
  gross_toman: number;
  discount_toman: number;
  status: 'pending' | 'paid' | 'consumed' | 'failed' | 'canceled' | 'refunded' | 'expired';
  ref_number: string;
  payment_url: string;
  invoice_number: string;
  invoice_url?: string;
}

export class FaeriePaylineClient {
  private readonly baseUrl: string;
  private readonly paylineToken: string;

  constructor(paylineToken: string, baseUrl: string = 'https://faerie.ir') {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.paylineToken = paylineToken.trim();
  }

  async getProducts(): Promise<FaerieProduct[]> {
    const data = await this.call('GET', '/api/v1/iap/products');
    return data.products ?? [];
  }

  async initPurchase(params: {
    sku: string;
    userId: string;
    mobile?: string;
    nationalCode?: string;
    discountCode?: string;
    amountToman?: number;
    returnUrl?: string;
  }): Promise<FaeriePurchase> {
    const data = await this.call('POST', '/api/v1/iap/init', {
      sku: params.sku,
      user_id: params.userId,
      mobile: params.mobile,
      national_code: params.nationalCode,
      discount_code: params.discountCode,
      amount: params.amountToman,
      return_url: params.returnUrl,
    });
    const p: FaeriePurchase = data.purchase ?? data;
    if (!p.payment_url && data.payment_url) p.payment_url = data.payment_url;
    return p;
  }

  async getStatus(purchaseId: string): Promise<FaeriePurchase> {
    return await this.call('GET', `/api/v1/iap/status/${encodeURIComponent(purchaseId)}`);
  }

  async consume(purchaseId: string, userId: string): Promise<FaeriePurchase> {
    return await this.call('POST', '/api/v1/iap/consume', {
      purchase_id: purchaseId,
      user_id: userId,
    });
  }

  async getInvoice(purchaseId: string): Promise<Record<string, unknown>> {
    return await this.call('GET', `/api/v1/iap/invoice/${encodeURIComponent(purchaseId)}`);
  }

  async openGateway(purchase: FaeriePurchase): Promise<void> {
    const deepUrl = `faerie://pay/${encodeURIComponent(purchase.purchase_id)}`;
    const canDeep = await Linking.canOpenURL(deepUrl).catch(() => false);
    await Linking.openURL(canDeep ? deepUrl : purchase.payment_url);
  }

  private async call(method: 'GET' | 'POST', path: string, body?: Record<string, unknown>): Promise<any> {
    const res = await fetch(this.baseUrl + path, {
      method,
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Payline': this.paylineToken,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      throw new Error(json.message || `خطا در ارتباط با درگاه فائری (${res.status})`);
    }
    return json.data;
  }
}

export interface FaeriePayButtonProps {
  client: FaeriePaylineClient;
  sku: string;
  userId: string;
  label?: string;
  autoConsume?: boolean;
  returnUrl?: string;
  onPaid: (purchase: FaeriePurchase) => void;
  onError?: (message: string) => void;
}

export const FaeriePayButton: React.FC<FaeriePayButtonProps> = ({
  client,
  sku,
  userId,
  label = 'پرداخت با فائری',
  autoConsume = true,
  returnUrl,
  onPaid,
  onError,
}) => {
  const [busy, setBusy] = useState(false);
  const pendingRef = useRef<string | null>(null);

  useEffect(() => {
    const sub = AppState.addEventListener('change', async (next) => {
      if (next === 'active' && pendingRef.current) {
        const pid = pendingRef.current;
        pendingRef.current = null;
        setBusy(true);
        try {
          let st = await client.getStatus(pid);
          if (st.status === 'paid' || st.status === 'consumed') {
            if (autoConsume && st.status === 'paid') {
              st = await client.consume(pid, userId);
            }
            onPaid(st);
          } else {
            onError?.(`وضعیت تراکنش: ${st.status}`);
          }
        } catch (err: any) {
          onError?.(err.message || 'خطا در بررسی وضعیت');
        } finally {
          setBusy(false);
        }
      }
    });
    return () => sub.remove();
  }, [client, userId, autoConsume, onPaid, onError]);

  const handlePress = async () => {
    setBusy(true);
    try {
      const tx = await client.initPurchase({ sku, userId, returnUrl });
      pendingRef.current = tx.purchase_id;
      await client.openGateway(tx);
    } catch (err: any) {
      onError?.(err.message || 'خطای شروع پرداخت');
    } finally {
      setBusy(false);
    }
  };

  return (
    <TouchableOpacity style={styles.btn} onPress={handlePress} disabled={busy} activeOpacity={0.85}>
      <View style={styles.badge}><Text style={styles.badgeTxt}>F</Text></View>
      <Text style={styles.txt}>{busy ? 'در حال اتصال…' : label}</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  btn: {
    flexDirection: 'row-reverse',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#5B5CE6',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 14,
  },
  badge: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.22)',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  badgeTxt: { color: '#FFF', fontWeight: '800', fontSize: 13 },
  txt: { color: '#FFF', fontWeight: '700', fontSize: 14 },
});
