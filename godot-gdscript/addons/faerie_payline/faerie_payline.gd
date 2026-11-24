extends Node
class_name FaeriePayline

## Faerie Payline SDK برای موتور بازی‌سازی Godot 4.x
## قابلیت استفاده به‌عنوان Autoload یا Node در صحنه

signal products_loaded(products: Array)
signal purchase_succeeded(purchase: Dictionary)
signal purchase_failed(message: String, requires_national_id: bool)
signal invoice_loaded(invoice: Dictionary)

@export var base_url: String = "https://faerie.ir"
@export var payline_token: String = "pl_YOUR_PAYLINE_TOKEN"
@export var auto_consume: bool = true

var _pending_purchase_id: String = ""
var _pending_user_id: String = ""

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_FOCUS_IN and _pending_purchase_id != "":
		var pid := _pending_purchase_id
		_pending_purchase_id = ""
		verify_purchase(pid, _pending_user_id)

func fetch_products() -> void:
	_send_request(HTTPClient.METHOD_GET, "/api/v1/iap/products", {}, func(ok: bool, root: Dictionary):
		if not ok:
			purchase_failed.emit(root.get("message", "خطای دریافت آیتم‌ها"), false)
			return
		var items: Array = root.get("data", {}).get("products", [])
		products_loaded.emit(items)
	)

func start_purchase(sku: String, user_id: String, mobile: String = "", national_code: String = "", discount_code: String = "", custom_amount: int = 0) -> void:
	var body := {"sku": sku, "user_id": user_id}
	if mobile != "": body["mobile"] = mobile
	if national_code != "": body["national_code"] = national_code
	if discount_code != "": body["discount_code"] = discount_code
	if custom_amount > 0: body["amount"] = custom_amount

	_pending_user_id = user_id
	_send_request(HTTPClient.METHOD_POST, "/api/v1/iap/init", body, func(ok: bool, root: Dictionary):
		if not ok:
			purchase_failed.emit(root.get("message", "شروع پرداخت ناموفق بود"), bool(root.get("requires_national_id", false)))
			return
		var data: Dictionary = root.get("data", {})
		var p: Dictionary = data.get("purchase", data)
		_pending_purchase_id = str(p.get("purchase_id", ""))
		var pay_url: String = str(data.get("payment_url", p.get("payment_url", "")))
		if pay_url != "":
			OS.shell_open(pay_url)
	)

func verify_purchase(purchase_id: String, user_id: String) -> void:
	_send_request(HTTPClient.METHOD_GET, "/api/v1/iap/status/" + purchase_id.uri_encode(), {}, func(ok: bool, root: Dictionary):
		if not ok:
			purchase_failed.emit(root.get("message", "خطا در استعلام پرداخت"), false)
			return
		var p: Dictionary = root.get("data", {})
		var st := str(p.get("status", ""))
		if st == "paid" or st == "consumed":
			if auto_consume and st == "paid":
				_send_request(HTTPClient.METHOD_POST, "/api/v1/iap/consume", {"purchase_id": purchase_id, "user_id": user_id}, func(c_ok: bool, c_root: Dictionary):
					purchase_succeeded.emit(c_root.get("data", p) if c_ok else p)
				)
			else:
				purchase_succeeded.emit(p)
		else:
			purchase_failed.emit("وضعیت پرداخت: " + st, false)
	)

func fetch_invoice(purchase_id: String) -> void:
	_send_request(HTTPClient.METHOD_GET, "/api/v1/iap/invoice/" + purchase_id.uri_encode(), {}, func(ok: bool, root: Dictionary):
		if ok:
			invoice_loaded.emit(root.get("data", {}))
	)

func _send_request(method: HTTPClient.Method, path: String, payload: Dictionary, done: Callable) -> void:
	var req := HTTPRequest.new()
	add_child(req)
	req.request_completed.connect(func(_result: int, code: int, _headers: PackedStringArray, body: PackedByteArray):
		req.queue_free()
		var parsed = JSON.parse_string(body.get_string_from_utf8())
		if typeof(parsed) != TYPE_DICTIONARY:
			done.call(false, {"message": "پاسخ نامعتبر سرور (" + str(code) + ")"})
			return
		var dict: Dictionary = parsed
		done.call(code >= 200 and code < 300 and bool(dict.get("success", false)), dict)
	)
	var clean_base := base_url.trim_suffix("/")
	var headers := PackedStringArray([
		"Accept: application/json",
		"X-Payline: " + payline_token,
		"Content-Type: application/json; charset=utf-8"
	])
	var body_str := "" if method == HTTPClient.METHOD_GET else JSON.stringify(payload)
	req.request(clean_base + path, headers, method, body_str)
