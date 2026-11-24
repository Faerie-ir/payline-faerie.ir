# Faerie Payline SDK — React Native & Expo (TypeScript)

کیت توسعهٔ رسمی **Faerie Payline** برای برنامه‌های **React Native** و **Expo** شامل کلاس `FaeriePaylineClient` و کامپوننت آمادهٔ `<FaeriePayButton />`.

## نمونهٔ استفاده
```tsx
import { FaeriePaylineClient, FaeriePayButton } from 'react-native-faerie-payline';

const payline = new FaeriePaylineClient('pl_YOUR_PAYLINE_TOKEN', 'https://faerie.ir');

export function UpgradeScreen() {
  return (
    <FaeriePayButton
      client={payline}
      sku="pro_monthly"
      userId="user_109"
      label="خرید اشتراک با فائری"
      onPaid={(tx) => alert(`پرداخت موفق! فاکتور: ${tx.invoice_number}`)}
      onError={(msg) => alert(msg)}
    />
  );
}
```
