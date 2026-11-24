# Faerie Payline SDK — Unity (C# / UPM Package)

پکیج رسمی پرداخت درون‌برنامه‌ای **فائری (`Faerie Payline`)** برای بازی‌سازان **Unity** (معادل بومی و سبک `Bazaar-IAP-in-Unity`، بدون نیاز به درگیری با تداخل Gradle، فایل‌های `.aar` یا کلاس‌های سنگین AndroidJavaObject).

## ویژگی‌ها
- سازگار با **Unity 2020.3 تا Unity 6** (خروجی‌های Android، Windows، WebGL و تست مستقیم داخل خود **Unity Editor**!).
- بررسی خودکار وضعیت پرداخت به محض بازگشت بازیکن از درگاه فائری به بازی (`OnApplicationFocus`).
- مصرف خودکار (`consume`) آیتم‌های مصرفی (سکه، الماس، انرژی) و برگرداندن شماره فاکتور رسمی.
- کامپوننت بدون کدنویسی **`FaeriePayUnityButton`** برای اتصال مستقیم به دکمه‌های Canvas در Inspector.

## روش نصب در Unity
### روش ۱: از طریق Unity Package Manager
در یونیتی به `Window → Package Manager → + → Add package from disk...` بروید و فایل `package.json` این پوشه را انتخاب کنید (یا لینک گیت مخزن را در `Add package from git URL...` وارد نمایید).

### روش ۲: کپی مستقیم اسکریپت‌ها
پوشهٔ `Runtime` را داخل `Assets/Plugins/FaeriePayline` کپی کنید.

## راه‌اندازی در ۳ دقیقه
1. یک `GameObject` خالی در صحنهٔ اول بازی بسازید، نامش را `FaeriePayline` بگذارید و اسکریپت `FaeriePaylineManager` را به آن بدهید.
2. در Inspector مقدار **`Payline Token`** (که با `pl_` شروع می‌شود و از پنل توسعه‌دهندگان فائری گرفته‌اید) را وارد کنید.
3. برای خرید با کد C#:

```csharp
using Faerie.Payline;
using UnityEngine;

public class CoinShop : MonoBehaviour
{
    public void Buy500Gems()
    {
        FaeriePaylineManager.Instance.BuyProduct(
            sku: "gems_500",
            playerUserId: PlayerPrefs.GetString("player_id", SystemInfo.deviceUniqueIdentifier),
            onSuccess: purchase =>
            {
                Debug.Log($"پرداخت موفق! فاکتور رسمی: {purchase.invoice_number}");
                GameWallet.AddGems(500);
            },
            onError: (errMsg, requiresNationalId) =>
            {
                Debug.LogWarning($"خطای خرید: {errMsg}");
            }
        );
    }
}
```
یا بدون هیچ کدنویسی، اسکریپت **`FaeriePayUnityButton`** را روی یک `UI Button` بیندازید و رویداد `onPurchaseSucceeded` را در Inspector وصل کنید!
