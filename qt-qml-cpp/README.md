# Faerie Payline SDK — C++ / Qt 6 & QML

کیت تک‌فایلی `FaeriePaylineQt.hpp` برای برنامه‌ها و بازی‌های **C++ / Qt 6 / QML** روی اندروید و دسکتاپ.

## ثبت در QML
```cpp
qmlRegisterType<FaeriePaylineQt>("ir.faerie.payline", 1, 0, "FaeriePayline");
```
و فراخوانی مستقیم `payline.startPurchase("sku_1", "user_1")` از دکمهٔ QML!
