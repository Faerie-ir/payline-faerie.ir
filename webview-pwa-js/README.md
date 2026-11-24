# Faerie Payline SDK — WebApps, PWA & Android WebView Bridge

کیت جامع اتصال **برنامه‌های وب (PWA / React / Vue / HTML)** و **برنامه‌های مبتنی بر Android WebView** به درگاه پرداخت **Faerie Payline**.

## بخش اول: تگ آمادهٔ وب (`<faerie-pay-button>`)
فقط با لود کردن یک اسکریپت در صفحهٔ وب خود، دکمهٔ آمادهٔ فائری را با یک تگ HTML رندر کنید:

```html
<script src="faerie-payline-widget.js"></script>

<faerie-pay-button
  sku="vip_monthly"
  user-id="user_42"
  init-endpoint="/api/checkout/faerie-init"
  label="پرداخت اشتراک با فائری">
</faerie-pay-button>
```
> **نکتهٔ امنیتی برای سایت‌های عمومی:** در وب عمومی، کلید `pl_...` را در بک‌اند خود (PHP/Node/Python) نگه دارید و آدرس اندپوینت بک‌اند خود را به `init-endpoint` بدهید. در بک‌اند هنگام صدا زدن `POST /api/v1/iap/init` پارامتر `return_url` را ارسال کنید تا کاربر پس از پرداخت در شاپرک، در عرض ۲ ثانیه خودکار با `?purchase_id=fp_...&status=paid` به سایت شما بازگردد!

---

## بخش دوم: پل اختصاصی Android WebView (`FaerieWebViewBridge.kt`)
اگر سایت یا برنامهٔ وب خود را داخل یک `WebView` اندروید قرار داده‌اید:
1. بدون این پل، کلیک روی لینک‌های `faerie://pay/...` در WebView با خطای `ERR_UNKNOWN_URL_SCHEME` مواجه می‌شود.
2. با اتصال `FaerieWebViewBridge`، هم لینک‌های عمیق فائری خودکار برنامه فائری را باز می‌کنند، و هم کلید `pl_...` امن داخل کد نیتیو اندروید باقی می‌ماند!

### کد اتصال در `MainActivity.kt` اندروید:
```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var faerieBridge: FaerieWebViewBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        faerieBridge = FaerieWebViewBridge(
            activity = this,
            webView = webView,
            paylineToken = "pl_YOUR_PAYLINE_TOKEN"
        )
        faerieBridge.attach()
        webView.loadUrl("https://my-webapp.ir")
    }

    override fun onResume() {
        super.onResume()
        faerieBridge.onActivityResume()
    }
}
```
### دریافت نتیجهٔ آنی در جاوااسکریپتِ صفحهٔ وب:
```javascript
window.addEventListener('faerie:purchase', (e) => {
  const tx = e.detail;
  if (tx.status === 'paid' || tx.status === 'consumed') {
    alert('پرداخت موفق! شماره فاکتور: ' + tx.invoice_number);
  }
});
```
