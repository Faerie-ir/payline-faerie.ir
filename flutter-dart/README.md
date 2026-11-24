# Faerie Payline SDK — Flutter (Dart)

پکیج رسمی **Faerie Payline** برای برنامه‌های **Flutter** شامل کلاینت کامل و ویجت دکمهٔ آمادهٔ **`FaeriePayButton`** با تشخیص خودکار بازگشت از درگاه (`AppLifecycleState.resumed`).

## نصب در `pubspec.yaml`
```yaml
dependencies:
  faerie_payline:
    path: ./faerie_payline # یا آدرس گیت مخزن
```

## نمونهٔ استفاده در Flutter
```dart
final payline = FaeriePaylineClient(
  baseUrl: 'https://faerie.ir',
  paylineToken: 'pl_YOUR_PAYLINE_TOKEN',
);

FaeriePayButton(
  client: payline,
  sku: 'premium_pack',
  userId: 'user_42',
  label: 'پرداخت با فائری',
  onPaid: (purchase) {
    debugPrint('خرید موفق! شماره فاکتور: ${purchase.invoiceNumber}');
  },
  onError: (err) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(err))),
)
```
