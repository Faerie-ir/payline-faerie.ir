# Faerie Payline SDK — B4A (Basic4Android)

ماژول آمادهٔ کلاس **`FaeriePayline.bas`** برای توسعه‌دهندگان **B4A (Basic4Android)**؛ فقط با کتابخانه‌های پیش‌فرض خود B4A (`OkHttpUtils2`, `JSON`, `Phone`).

## نصب و استفاده در B4A
۱. در محیط B4A از منوی `Project → Add Existing Modules` فایل `FaeriePayline.bas` را اضافه کنید.
۲. در `Sub Globals` و `Activity_Create` و `Activity_Resume`:

```vbnet
Sub Globals
    Private Payline As FaeriePayline
End Sub

Sub Activity_Create(FirstTime As Boolean)
    Payline.Initialize(Me, "Payline", "https://faerie.ir", "pl_YOUR_PAYLINE_TOKEN")
End Sub

Sub BtnBuy_Click
    Payline.StartPurchase("coins_100", "user_101", "09121234567", "", "")
End Sub

Sub Activity_Resume
    If Payline.LastPurchaseId <> "" Then
        Dim pid As String = Payline.LastPurchaseId
        Payline.LastPurchaseId = ""
        Payline.VerifyPurchase(pid)
    End If
End Sub

Sub Payline_PurchaseSucceeded(PurchaseData As Map)
    Dim inv As String = PurchaseData.GetDefault("invoice_number", "")
    Payline.ConsumePurchase(PurchaseData.Get("purchase_id"), "user_101")
    ToastMessageShow("خرید موفق! شماره فاکتور: " & inv, True)
End Sub

Sub Payline_PurchaseFailed(Reason As String)
    ToastMessageShow(Reason, True)
End Sub
```
