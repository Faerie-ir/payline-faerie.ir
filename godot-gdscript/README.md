# Faerie Payline SDK — Godot Engine 4.x (GDScript)

افزونهٔ رسمی پرداخت درون‌برنامه‌ای **فائری (`Faerie Payline`)** برای موتور بازی‌سازی **Godot 4.x** (خروجی Android، دسکتاپ و وب).

## نصب و استفاده
۱. پوشهٔ `addons/faerie_payline` را داخل پروژهٔ Godot خود کپی کنید.
۲. اسکریپت `faerie_payline.gd` را در `Project Settings → Autoload` با نام `Payline` ثبت کنید.
۳. نمونهٔ کد خرید در GDScript:

```gdscript
func _ready() -> void:
    Payline.payline_token = "pl_YOUR_PAYLINE_TOKEN"
    Payline.purchase_succeeded.connect(_on_paid)
    Payline.purchase_failed.connect(_on_error)

func _on_buy_sword_pressed() -> void:
    Payline.start_purchase("legendary_sword", "player_42")

func _on_paid(purchase: Dictionary) -> void:
    print("پرداخت موفق! فاکتور: ", purchase.get("invoice_number", ""))
    unlock_sword()

func _on_error(msg: String, _needs_nat: bool) -> void:
    print("خطای پرداخت: ", msg)
```
