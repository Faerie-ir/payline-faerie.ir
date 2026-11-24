# Faerie Payline SDK — .NET / C# (.NET MAUI, Xamarin.Android, ASP.NET Core, WPF)

کلاینت رسمی و مدرن C# برای اکوسیستم **.NET 8+** شامل برنامه‌های موبایل **.NET MAUI / Xamarin** و سرویس‌های بک‌اند **ASP.NET Core**.

## نمونهٔ استفاده در .NET MAUI / ASP.NET Core
```csharp
using var payline = new Faerie.Payline.DotNet.FaeriePaylineClient("pl_YOUR_PAYLINE_TOKEN");
var tx = await payline.InitPurchaseAsync("pro_plan", "user_55", returnUrl: "myapp://callback");
await Launcher.Default.OpenAsync(tx.PaymentUrl);

// پس از بازگشت کاربر:
var verified = await payline.GetStatusAsync(tx.PurchaseId);
if (verified.IsPaid)
{
    await payline.ConsumeAsync(tx.PurchaseId, "user_55");
}
```
