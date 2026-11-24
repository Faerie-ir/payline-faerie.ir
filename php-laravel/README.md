# Faerie Payline SDK — PHP 8+ & Laravel

کیت توسعهٔ رسمی سمت سرور برای **PHP 8+** خام، **Laravel**، **Symfony** و سایر فریم‌ورک‌های PHP بدون هیچ وابستگی خارجی.

## ۱) استفاده در PHP خام (شروع پرداخت + Callback بازگشت)
```php
<?php
require_once __DIR__ . '/src/FaeriePayline.php';

use Faerie\Payline\FaeriePayline;

$payline = new FaeriePayline('pl_YOUR_PAYLINE_TOKEN', 'https://faerie.ir');

// مرحلهٔ اول: ارسال کاربر به درگاه فائری
if ($_GET['action'] === 'pay') {
    $tx = $payline->initPurchase([
        'sku'           => 'vip_monthly',
        'user_id'       => 'user_42',
        'mobile'        => '09123456789',
        'national_code' => '0012345678',
        'return_url'    => 'https://mysite.ir/checkout.php?action=callback',
    ]);
    header('Location: ' . $tx['payment_url']);
    exit;
}

// مرحلهٔ دوم: بازگشت خودکار کاربر از شاپرک و تأیید سمت سرور
if ($_GET['action'] === 'callback' && !empty($_GET['purchase_id'])) {
    $verified = $payline->verifyAndConsume($_GET['purchase_id'], 'user_42');
    echo 'پرداخت موفق! شماره فاکتور رسمی فائری: ' . htmlspecialchars($verified['invoice_number']);
}
```

## ۲) استفاده در Laravel (`CheckoutController.php`)
در فایل `.env` مقدار `FAERIE_PAYLINE_TOKEN=pl_...` را قرار دهید و در کنترلر بنویسید:
```php
public function start(Request $request)
{
    $payline = new \Faerie\Payline\FaeriePayline(config('services.faerie.payline_token'));
    $tx = $payline->initPurchase([
        'sku'           => $request->string('sku')->toString(),
        'user_id'       => (string) auth()->id(),
        'mobile'        => $request->string('mobile')->toString(),
        'national_code' => $request->string('national_code')->toString(),
        'return_url'    => route('checkout.faerie.callback'),
    ]);
    return redirect()->away($tx['payment_url']);
}

public function callback(Request $request)
{
    $payline = new \Faerie\Payline\FaeriePayline(config('services.faerie.payline_token'));
    $tx = $payline->verifyAndConsume($request->query('purchase_id'), (string) auth()->id());
    return view('checkout.success', ['purchase' => $tx]);
}
```
