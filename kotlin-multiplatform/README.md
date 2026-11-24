# Faerie Payline SDK — Kotlin Multiplatform (KMP / Compose Multiplatform)

کیت چندسکویی **Faerie Payline** برای پروژه‌های **Kotlin Multiplatform** و **Compose Multiplatform** (Android, Desktop JVM, iOS, Web) بر پایهٔ `Ktor` و `kotlinx.serialization`.

## نصب و استفاده در `commonMain`
فایل `FaeriePaylineKmp.kt` را در `commonMain` قرار دهید:

```kotlin
val payline = FaeriePaylineKmp(
    httpClient = HttpClient(),
    baseUrl = "https://faerie.ir",
    paylineToken = "pl_YOUR_PAYLINE_TOKEN"
)

val tx = payline.initPurchase(sku = "pro_upgrade", userId = "u_99")
// باز کردن tx.paymentUrl در مرورگر سیستم یا دیپ‌لینک faerie://pay/${tx.purchaseId}
val verified = payline.getStatus(tx.purchaseId)
if (verified.isPaid) {
    // فعال‌سازی قابلیت ویژه
}
```
