# Faerie Payline SDK — Android (Kotlin + Jetpack Compose)

کامپوننت و کیت رسمی پرداخت درون‌برنامه‌ای **فائری (`Faerie Payline`)** برای برنامه‌های مدرن اندروید با **Kotlin و Jetpack Compose** (مشابه مدل `BazaarPay`، کاملاً مستقل و بدون نیاز به Retrofit/OkHttp).

## امکانات کلیدی
- کامپوننت گرافیکی آمادهٔ دکمهٔ **`FaeriePayButton`** با طراحی راست‌به‌چپ فارسی.
- شیت کامل پرداخت درون‌برنامه‌ای **`FaeriePaySheet`** شامل:
  - دریافت زندهٔ مشخصات و قیمت آیتم از سرور فائری (`GET /api/v1/iap/products`).
  - پشتیبانی از آیتم‌های مصرفی (`consumable`)، دائمی (`nonconsumable`) و مبلغ دلخواه (`custom_amount`).
  - پشتیبانی از کد تخفیف و فرم هوشمند کد ملی / موبایل (در صورت فعال بودن تطبیق شاهکار روی سرور).
  - باز کردن مستقیم برنامه فائری (`faerie://pay/{purchase_id}`) و در صورت نصب نبودن، باز کردن صفحهٔ واسط رسمی (`https://faerie.ir/pay/{purchase_id}`).
  - استعلام خودکار وضعیت در `ON_RESUME` به‌محض بازگشت کاربر به برنامه (`GET /api/v1/iap/status/{id}`).
  - مصرف خودکار آیتم‌های مصرفی (`POST /api/v1/iap/consume`) و دریافت فاکتور رسمی (`GET /api/v1/iap/invoice/{id}`).

## نحوهٔ نصب در Android Studio
۱. ماژول `payline-compose` (یا دو فایل `FaeriePaylineClient.kt` و `FaeriePaySheet.kt`) را به پروژهٔ خود اضافه کنید.
۲. در پنل توسعه‌دهندگان فائری (`/dev`) وارد برنامهٔ خود شوید:
   - در بخش **«آیتم‌های خرید»**، شناسهٔ محصول (`sku`) مثلاً `coins_500` را بسازید.
   - در بخش **«خط پرداخت (Payline)»**، توکن برنامهٔ خود (با پیشوند `pl_`) را کپی کنید.
۳. در `AndroidManifest.xml` مجوز اینترنت را مطمئن شوید:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## نمونهٔ استفاده (فقط در ۵ خط!)

```kotlin
val payline = remember {
    FaeriePaylineClient(
        baseUrl = "https://faerie.ir",
        paylineToken = "pl_0123456789abcdef0123456789abcdef01234567"
    )
}

FaeriePayButton(
    client = payline,
    sku = "coins_500",
    userId = currentUser.id, // شناسهٔ یکتای کاربر در برنامهٔ شما
    label = "خرید ۵۰۰ سکه با فائری",
    onPaid = { purchase, invoice ->
        // پرداخت در سرور تأیید و consume شده است؛ سکه را تحویل دهید!
        grantCoinsToUser(500, purchase.purchaseId)
    },
    onError = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
)
```

## استفادهٔ بدون UI (مستقیم از `FaeriePaylineClient`)
اگر می‌خواهید رابط کاربری فروشگاه خودتان را داشته باشید، مستقیم متدهای کلاینت را در `withContext(Dispatchers.IO)` صدا بزنید:
1. `client.getProducts()`
2. `val tx = client.initPurchase(sku = "vip_monthly", userId = "u_42")`
3. باز کردن `tx.paymentUrl` در مرورگر/برنامه فائری
4. پس از بازگشت کاربر: `val result = client.getStatus(tx.purchaseId)`
5. اگر `result.isPaid` برقرار بود: تحویل کالا + برای آیتم مصرفی `client.consume(tx.purchaseId, "u_42")` + نمایش `client.getInvoice(tx.purchaseId)`.

---
**English Summary:** Official Kotlin & Jetpack Compose SDK for **Faerie Payline** in-app billing (`FaeriePayButton` + `FaeriePaySheet` + zero-dependency `FaeriePaylineClient`).
