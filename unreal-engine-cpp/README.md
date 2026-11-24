# Faerie Payline SDK — Unreal Engine 5 (C++ & Blueprints)

زیرسیستم رسمی `UGameInstanceSubsystem` برای اتصال بازی‌های **Unreal Engine 5** (در Blueprint و C++) به درگاه پرداخت درون‌برنامه‌ای **Faerie Payline**.

## نصب در پروژهٔ Unreal
۱. در فایل `YourGame.Build.cs` ماژول‌های `"HTTP"` و `"Json"` و `"JsonUtilities"` را به `PublicDependencyModuleNames` اضافه کنید.
۲. فایل‌های `FaeriePaylineSubsystem.h` و `FaeriePaylineSubsystem.cpp` را در پوشهٔ `Source` پروژه کپی کنید.
۳. در **Blueprint** مستقیم نود `Get FaeriePaylineSubsystem` را صدا بزنید، `PaylineToken` را مقداردهی کنید و نود `Start Purchase` و رویداد `On Purchase Succeeded` را متصل نمایید!
