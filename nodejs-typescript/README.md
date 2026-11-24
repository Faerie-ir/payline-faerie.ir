# Faerie Payline SDK — Node.js & TypeScript (Express / NestJS / Next.js)

کیت سمت سرور بدون وابستگی برای **Node.js 18+**، **Express**، **Fastify**، **NestJS** و **Next.js App Router**.

## نمونهٔ استفاده در Next.js / Express
```ts
import { FaeriePaylineServer } from '@faerie/payline-node';

const payline = new FaeriePaylineServer(process.env.FAERIE_PAYLINE_TOKEN!);

// ۱) شروع تراکنش
const tx = await payline.initPurchase({
  sku: 'pro_annual',
  userId: 'user_88',
  mobile: '09123456789',
  nationalCode: '0012345678',
  returnUrl: 'https://myapp.ir/api/payline/callback',
});
// هدایت کاربر به tx.payment_url

// ۲) در مسیر Callback پس از بازگشت کاربر:
const verified = await payline.verifyAndConsume(req.query.purchase_id, 'user_88');
console.log('فاکتور رسمی:', verified.invoice_number);
```
