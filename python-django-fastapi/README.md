# Faerie Payline SDK — Python (Django, FastAPI, Flask)

کلاینت رسمی پایتون برای **Faerie Payline** فقط با کتابخانهٔ استاندارد پایتون (بدون نیاز به نصب پکیج خارجی!).

## نمونهٔ استفاده در FastAPI / Django
```python
from faerie_payline import FaeriePaylineClient

payline = FaeriePaylineClient(payline_token="pl_YOUR_PAYLINE_TOKEN")

# ۱) ایجاد لینک پرداخت
tx = payline.init_purchase(
    sku="gold_pack",
    user_id="user_42",
    mobile="09123456789",
    national_code="0012345678",
    return_url="https://api.example.ir/payline/callback",
)
print("Redirect user to:", tx["payment_url"])

# ۲) تأیید و مصرف در Callback
verified = payline.verify_and_consume(tx["purchase_id"], user_id="user_42")
print("Invoice:", verified["invoice_number"])
```
