# Faerie Payline SDK — Android (Pure Java / Android Studio XML)

کیت توسعهٔ رسمی **Faerie Payline** به زبان **Java خالص** برای پروژه‌های کلاسیک Android Studio (بدون هیچ کتابخانهٔ اضافی در Gradle).

## نصب سریع
فایل `src/main/java/ir/faerie/payline/java/FaeriePayline.java` را در پروژهٔ Android Studio خود کپی کنید و در `AndroidManifest.xml` مجوز اینترنت (`android.permission.INTERNET`) را قرار دهید.

## نمونهٔ کامل در Activity (Java)

```java
public class ShopActivity extends AppCompatActivity {
    private FaeriePayline payline;
    private String pendingPurchaseId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        payline = new FaeriePayline("https://faerie.ir", "pl_YOUR_40_HEX_TOKEN");

        LinearLayout container = findViewById(R.id.shop_container);
        container.addView(FaeriePayline.createPayButton(this, "پرداخت با فائری (۱۰۰ سکه)", () -> {
            payline.initPurchase("coins_100", "user_84", "09121234567", null, null, 0, null,
                new FaeriePayline.Callback<FaeriePayline.Purchase>() {
                    @Override
                    public void onSuccess(FaeriePayline.Purchase p) {
                        pendingPurchaseId = p.purchaseId;
                        FaeriePayline.openGateway(ShopActivity.this, p);
                    }
                    @Override
                    public void onError(String message, int httpCode, boolean requiresNationalId) {
                        Toast.makeText(ShopActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingPurchaseId != null) {
            final String pid = pendingPurchaseId;
            pendingPurchaseId = null;
            payline.checkStatus(pid, new FaeriePayline.Callback<FaeriePayline.Purchase>() {
                @Override
                public void onSuccess(FaeriePayline.Purchase p) {
                    if (p.isPaid()) {
                        payline.consume(p.purchaseId, "user_84", new FaeriePayline.Callback<FaeriePayline.Purchase>() {
                            @Override
                            public void onSuccess(FaeriePayline.Purchase done) {
                                Toast.makeText(ShopActivity.this,
                                    "خرید انجام شد! شماره فاکتور: " + done.invoiceNumber,
                                    Toast.LENGTH_LONG).show();
                            }
                            @Override
                            public void onError(String msg, int code, boolean reqNid) {}
                        });
                    }
                }
                @Override
                public void onError(String message, int code, boolean reqNid) {
                    Toast.makeText(ShopActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
```

---
**English Summary:** Zero-dependency pure Java SDK for **Faerie Payline** Android apps, including background executor threading, `MainLooper` callbacks, and a programmatic `createPayButton` view.
