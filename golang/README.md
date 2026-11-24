# Faerie Payline SDK — Go (Golang)

کلاینت رسمی زبان **Go** برای درگاه **Faerie Payline** (فقط با کتابخانهٔ استاندارد Go و بدون هیچ پکیج خارجی).

## نمونهٔ استفاده
```go
client := payline.NewClient("pl_YOUR_PAYLINE_TOKEN")
tx, err := client.InitPurchase(ctx, payline.InitParams{
    SKU:       "gems_1000",
    UserID:    "player_9",
    ReturnURL: "https://game.example.ir/payline/callback",
})
// پس از بازگشت کاربر:
st, err := client.GetStatus(ctx, tx.PurchaseID)
if err == nil && st.IsPaid() {
    _, _ = client.Consume(ctx, tx.PurchaseID, "player_9")
}
```
