# Faerie Payline SDK — Construct 3 / GameMaker / Phaser / GDevelop (HTML5 Games)

کیت سبک جاوااسکریپت برای موتورهای بازی‌سازی مبتنی بر HTML5 (مانند **Construct 3**، **GameMaker**، **Phaser** و **GDevelop**).

## استفاده در Construct 3 (بلوک JavaScript در Event Sheet)
فایل `faerie-payline-game.js` را در پوشهٔ `Scripts` پروژه با هدف `Startup` ایمپورت کنید:

```javascript
globalThis.faerie = new FaerieGamePayline({
  baseUrl: "https://faerie.ir",
  paylineToken: "pl_YOUR_PAYLINE_TOKEN"
});

globalThis.faerie.onAutoVerified = (purchase) => {
  runtime.globalVars.PlayerCoins += 500;
  alert("خرید موفق! شماره فاکتور: " + purchase.invoice_number);
};

// هنگام کلیک روی دکمهٔ خرید:
await globalThis.faerie.buy("coins_500", runtime.globalVars.PlayerId);
```
