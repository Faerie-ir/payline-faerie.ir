# Faerie Payline SDK — Capacitor / Ionic / Cordova

کلاینت رسمی اتصال برنامه‌های هیبریدی **Ionic، Capacitor و Apache Cordova** به درگاه **Faerie Payline**.

## نمونهٔ استفاده با `@capacitor/app`
```ts
import { App } from '@capacitor/app';
import { CapacitorFaeriePayline } from './faerie-capacitor';

const payline = new CapacitorFaeriePayline({
  paylineToken: 'pl_YOUR_PAYLINE_TOKEN',
  returnScheme: 'myapp://faerie-return',
});

App.addListener('appUrlOpen', async ({ url }) => {
  const u = new URL(url);
  const pid = u.searchParams.get('purchase_id');
  if (pid) {
    const paid = await payline.verifyAndConsume(pid, 'user_42');
    console.log('Invoice:', paid.invoice_number);
  }
});
```
