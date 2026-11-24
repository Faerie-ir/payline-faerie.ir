# اکوسیستم Faerie Payline — مجموعهٔ رسمی کیت‌های توسعه و افزونه‌های درگاه فیری

مخزن جامع و رسمی **Faerie Payline** برای اتصال هر نوع اپلیکیشن موبایل، بازی، اپلیکیشن وب (PWA)، اپلیکیشن مبتنی بر Android WebView، فروشگاه وردپرسی/ووکامرس و سرویس بک‌اند به خط پرداخت درون‌برنامه‌ای و تحت وب مارکت اندرویدی **فیری** طراحی شده است.

برخلاف کیت‌های سنتی مبتنی بر AIDL که به کتابخانه‌های سنگین، سرویس پس‌زمینه و وابستگی‌های شکنندهٔ Gradle نیاز دارند، **Faerie Payline** بر پایهٔ معماری مدرن **REST + دیپ‌لینک (`faerie://pay/{purchase_id}`) + بازگشت از وب (`return_url`) + استعلام قطعی سمت سرور** بنا شده است. به همین دلیل روی **۱۰۰٪ دستگاه‌ها** — چه اپلیکیشن فیری نصب باشد و چه پرداخت از طریق مرورگر و شاپرک انجام شود — بدون خطا کار می‌کند و برای هر تراکنش موفق **فاکتور رسمی مارکت فیری** صادر می‌کند.

---

## فهرست کامل ۲۲ کیت و افزونهٔ این مجموعه

### ۱. توسعهٔ بومی اندروید (Android Native)

| پوشه | پلتفرم / زبان | محتویات کلیدی |
|---|---|---|
| [`android-kotlin-jetpack-compose`](./android-kotlin-jetpack-compose) | **Android Studio — Kotlin و Jetpack Compose** | کلاینت Coroutine (`FaeriePaylineClient.kt`) + کامپوننت گرافیکی آمادهٔ دکمهٔ `FaeriePayButton` و باتم‌شیت کامل خرید `FaeriePaySheet` (مشابه BazaarPay) با بررسی خودکار `ON_RESUME` |
| [`android-java`](./android-java) | **Android Studio — جاوا خالص** | تک‌فایل خالص جاوا (`FaeriePayline.java`) بدون هیچ وابستگی خارجی + سازندهٔ دکمهٔ آمادهٔ `createPayButton` |
| [`kotlin-multiplatform`](./kotlin-multiplatform) | **Kotlin Multiplatform (KMP)** | ماژول `commonMain` مبتنی بر Ktor و `kotlinx.serialization` برای اشتراک کد بین اندروید، دسکتاپ و iOS |
| [`b4a-basic4android`](./b4a-basic4android) | **B4A (Basic4Android)** | کلاس ماژول آمادهٔ `FaeriePayline.bas` بر پایهٔ `OkHttpUtils2` برای توسعه‌دهندگان ایرانی B4A |

### ۲. موتورهای بازی‌سازی (Game Engines)

| پوشه | موتور بازی | محتویات کلیدی |
|---|---|---|
| [`unity-csharp`](./unity-csharp) | **Unity (۲۰۲۰.۳ تا Unity 6)** | پکیج رسمی UPM (`package.json`) + `FaeriePaylineManager.cs` با تشخیص خودکار `OnApplicationFocus` + کامپوننت بدون کدنویسی `FaeriePayUnityButton.cs` |
| [`godot-gdscript`](./godot-gdscript) | **Godot Engine 4.x** | افزونهٔ رسمی `addons/faerie_payline` با سیگنال‌های آماده و بررسی خودکار بازگشت بازیکن به بازی |
| [`unreal-engine-cpp`](./unreal-engine-cpp) | **Unreal Engine 5 (C++ و Blueprint)** | زیرسیستم `UFaeriePaylineSubsystem` قابل فراخوانی مستقیم در Blueprint و C++ |
| [`construct-gamemaker-html5`](./construct-gamemaker-html5) | **Construct 3 / GameMaker / Phaser / GDevelop** | اسکریپت مستقل `faerie-payline-game.js` با پشتیبانی هم‌زمان از مرورگر و پل نیتیو WebView |

### ۳. فریم‌ورک‌های چندسکویی موبایل و دسکتاپ (Cross-Platform)

| پوشه | فریم‌ورک | محتویات کلیدی |
|---|---|---|
| [`flutter-dart`](./flutter-dart) | **Flutter (Dart 3)** | پکیج کامل فلاتر همراه با ویجت آمادهٔ راست‌به‌چپ `FaeriePayButton` و `WidgetsBindingObserver` |
| [`react-native`](./react-native) | **React Native و Expo** | کلاینت TypeScript + کامپوننت آمادهٔ `<FaeriePayButton />` با پایش `AppState` |
| [`capacitor-ionic-cordova`](./capacitor-ionic-cordova) | **Capacitor / Ionic / Cordova** | کلاینت تایپ‌اسکریپت برای اپلیکیشن‌های هیبریدی با بازگشت از طریق Custom URL Scheme |
| [`dotnet-maui-xamarin`](./dotnet-maui-xamarin) | **‎.NET 8 / ‎.NET MAUI / Xamarin / WPF** | کتابخانهٔ مدرن C# (`FaeriePaylineClient.cs`) با پشتیبانی `async/await` |
| [`qt-qml-cpp`](./qt-qml-cpp) | **C++ / Qt 6 و QML** | هدر تک‌فایلی `FaeriePaylineQt.hpp` قابل ثبت مستقیم به‌عنوان کامپوننت QML |

### ۴. اپلیکیشن‌های وب (PWA)، فرانت‌اند و اپلیکیشن‌های Android WebView

| پوشه | کاربرد | محتویات کلیدی |
|---|---|---|
| [`webview-pwa-js`](./webview-pwa-js) | **WebApp / PWA / React / Vue + Android WebView** | ۱) تگ استاندارد وب `<faerie-pay-button>` با Shadow DOM؛ ۲) پل نیتیو اندروید `FaerieWebViewBridge.kt` برای رفع خطای `ERR_UNKNOWN_URL_SCHEME` روی `faerie://`، حفظ امنیت توکن در کد نیتیو و ارسال رویداد `faerie:purchase` به جاوااسکریپت |

### ۵. سیستم‌های مدیریت محتوا و فروشگاه‌سازها (CMS و E-Commerce)

| پوشه | پلتفرم | محتویات کلیدی |
|---|---|---|
| [`wordpress-plugin`](./wordpress-plugin) | **WordPress و WooCommerce** | افزونهٔ کامل و آمادهٔ نصب (`faerie-payline-gateway.zip`) شامل درگاه رسمی تسویه‌حساب **ووکامرس** + شورت‌کد مستقل **`[faerie_pay]`** برای سایت‌های بدون ووکامرس |

### ۶. زبان‌ها و فریم‌ورک‌های سمت سرور (Backend SDKs و API Tools)

| پوشه | زبان / فریم‌ورک | محتویات کلیدی |
|---|---|---|
| [`php-laravel`](./php-laravel) | **PHP 8+ و Laravel / Symfony** | پکیج Composer بدون وابستگی (`Faerie\Payline\FaeriePayline`) با متد `verifyAndConsume` |
| [`nodejs-typescript`](./nodejs-typescript) | **Node.js 18+ / TypeScript / Express / NestJS / Next.js** | پکیج ESM بدون وابستگی خارجی مبتنی بر `fetch` بومی Node 18+ |
| [`python-django-fastapi`](./python-django-fastapi) | **Python 3.9+ (Django / FastAPI / Flask)** | ماژول تک‌فایلی `faerie_payline.py` بدون وابستگی خارجی |
| [`golang`](./golang) | **Go (Golang 1.21+)** | پکیج استاندارد Go (`payline.go`) فقط با `net/http` |
| [`rust`](./rust) | **Rust (Tokio / Axum / Actix)** | کریت ناهمگام مبتنی بر `reqwest` و `serde` |
| [`ruby`](./ruby) | **Ruby و Ruby on Rails** | ماژول خالص Ruby (`Faerie::PaylineClient`) |
| [`cli-curl-postman`](./cli-curl-postman) | **OpenAPI 3.1 + Postman + Bash CLI** | مشخصات کامل `openapi.yaml`، کالکشن آمادهٔ Postman/Insomnia و اسکریپت ترمینال `faerie-payline-cli.sh` |

---

## جریان استاندارد ۵ مرحله‌ای پرداخت در Faerie Payline

1. **دریافت کلید خط پرداخت (`pl_...`):** در پنل توسعه‌دهندگان فیری (`/dev/apps/{id}/payline`) خط پرداخت اپلیکیشن را فعال و آیتم‌ها (`SKU`) را تعریف کنید.
2. **شروع خرید (`POST /api/v1/iap/init`):** شناسهٔ آیتم (`sku`)، شناسهٔ کاربر (`user_id`)، موبایل و کد ملی خریدار (در صورت فعال بودن احراز هویت شاپرکی) و در صورت نیاز `return_url` (برای وب‌سایت، ووکامرس یا دیپ‌لینک اپلیکیشن) را ارسال کنید و `payment_url` را دریافت نمایید.
3. **هدایت کاربر به درگاه فیری:** لینک `payment_url` (یا دیپ‌لینک `faerie://pay/{purchase_id}`) را باز کنید؛ اگر اپلیکیشن فیری نصب باشد پرداخت در شیت بومی اپلیکیشن انجام می‌شود و در غیر این صورت از طریق مرورگر و درگاه بانکی شاپرک ادامه می‌یابد.
4. **استعلام قطعی (`GET /api/v1/iap/status/{purchase_id}`):** پس از بازگشت کاربر، وضعیت را از سرور فیری استعلام کنید. تنها وضعیت‌های **`paid`** و **`consumed`** به معنای موفقیت قطعی پرداخت هستند.
5. **مصرف آیتم مصرفی (`POST /api/v1/iap/consume`) و دریافت فاکتور رسمی (`GET /api/v1/iap/invoice/{purchase_id}`):** برای آیتم‌های مصرفی (سکه، الماس، شارژ کیف‌پول) پس از تحویل به کاربر، متد `consume` را صدا بزنید.
