package ir.faerie.payline

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Faerie Payline SDK — کلاینت رسمی پرداخت درون‌برنامه‌ای فائری (Kotlin / Android).
 *
 * بدون نیاز به هیچ کتابخانهٔ شبکه‌ای خارجی (صرفاً HttpURLConnection + org.json).
 * تمامی متدها مسدودکننده هستند و باید در Dispatchers.IO فراخوانی شوند.
 */
class FaeriePaylineClient(
    baseUrl: String = "https://faerie.ir",
    private val paylineToken: String
) {
    val marketBaseUrl: String = baseUrl.trim().trimEnd('/')

    data class Product(
        val sku: String,
        val title: String,
        val description: String,
        val type: String,
        val priceToman: Int,
        val customAmount: Boolean,
        val minToman: Int,
        val maxToman: Int
    ) {
        val isConsumable: Boolean get() = type == "consumable"
    }

    data class Purchase(
        val purchaseId: String,
        val sku: String,
        val amountToman: Int,
        val grossToman: Int,
        val discountToman: Int,
        val discountCode: String,
        val status: String,
        val refNumber: String,
        val paymentUrl: String,
        val createdAt: String,
        val paidAt: String?,
        val consumedAt: String?,
        val invoiceNumber: String,
        val invoiceUrl: String
    ) {
        val isPaid: Boolean get() = status == "paid" || status == "consumed"
    }

    data class InvoiceItem(
        val title: String,
        val qty: Int,
        val unitToman: Int,
        val totalToman: Int,
        val kind: String
    )

    data class Invoice(
        val purchaseId: String,
        val invoiceNumber: String,
        val status: String,
        val statusLabel: String,
        val issuedAt: String,
        val subtotalToman: Int,
        val discountToman: Int,
        val totalToman: Int,
        val refNumber: String,
        val cardPan: String,
        val items: List<InvoiceItem>
    )

    class PaylineException(
        message: String,
        val httpCode: Int = 0,
        val errorCode: String = "",
        val requiresNationalId: Boolean = false,
        cause: Throwable? = null
    ) : Exception(message, cause)

    /** ۱. دریافت فهرست آیتم‌های خرید فعال برنامه */
    @Throws(PaylineException::class)
    fun getProducts(): List<Product> {
        val data = request("GET", "/api/v1/iap/products")
        val arr = data.optJSONArray("products") ?: JSONArray()
        return (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            Product(
                sku = o.optString("sku"),
                title = o.optString("title"),
                description = o.optString("description"),
                type = o.optString("type", "consumable"),
                priceToman = o.optInt("price_toman", 0),
                customAmount = o.optBoolean("custom_amount", false),
                minToman = o.optInt("min_toman", 0),
                maxToman = o.optInt("max_toman", 0)
            )
        }
    }

    /** ۲. شروع تراکنش خرید و دریافت لینک صفحهٔ واسط فائری */
    @Throws(PaylineException::class)
    fun initPurchase(
        sku: String,
        userId: String,
        mobile: String? = null,
        nationalCode: String? = null,
        discountCode: String? = null,
        customAmountToman: Int? = null,
        returnUrl: String? = null
    ): Purchase {
        val body = JSONObject()
            .put("sku", sku.trim())
            .put("user_id", userId.trim())
        if (!mobile.isNullOrBlank()) body.put("mobile", mobile.trim())
        if (!nationalCode.isNullOrBlank()) body.put("national_code", nationalCode.trim())
        if (!discountCode.isNullOrBlank()) body.put("discount_code", discountCode.trim())
        if (customAmountToman != null && customAmountToman > 0) body.put("amount", customAmountToman)
        if (!returnUrl.isNullOrBlank()) body.put("return_url", returnUrl.trim())

        val data = request("POST", "/api/v1/iap/init", body)
        val pObj = data.optJSONObject("purchase") ?: data
        val payUrl = data.optString("payment_url").ifBlank { pObj.optString("payment_url") }
        return parsePurchase(pObj, payUrl)
    }

    /** ۳. بررسی وضعیت تراکنش از سرور فائری (منبع حقیقت قطعی پرداخت) */
    @Throws(PaylineException::class)
    fun getStatus(purchaseId: String): Purchase {
        val data = request("GET", "/api/v1/iap/status/" + URLEncoder.encode(purchaseId, "UTF-8"))
        return parsePurchase(data, data.optString("payment_url"))
    }

    /** ۴. ثبت تحویل و مصرف آیتم خرید مصرفی (Idempotent) */
    @Throws(PaylineException::class)
    fun consume(purchaseId: String, userId: String): Purchase {
        val body = JSONObject()
            .put("purchase_id", purchaseId.trim())
            .put("user_id", userId.trim())
        val data = request("POST", "/api/v1/iap/consume", body)
        return parsePurchase(data, data.optString("payment_url"))
    }

    /** ۵. دریافت تاریخچهٔ خریدهای یک کاربر در همین برنامه */
    @Throws(PaylineException::class)
    fun getUserPurchases(userId: String): List<Purchase> {
        val path = "/api/v1/iap/purchases?user_id=" + URLEncoder.encode(userId.trim(), "UTF-8")
        val raw = rawCall("GET", path, null)
        val arr = raw.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            parsePurchase(o, o.optString("payment_url"))
        }
    }

    /** ۶. دریافت فاکتور و صورت‌حساب رسمی تراکنش پرداخت‌شده */
    @Throws(PaylineException::class)
    fun getInvoice(purchaseId: String): Invoice {
        val d = request("GET", "/api/v1/iap/invoice/" + URLEncoder.encode(purchaseId, "UTF-8"))
        val itemsArr = d.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsArr.length()).map { i ->
            val it = itemsArr.getJSONObject(i)
            InvoiceItem(
                title = it.optString("title"),
                qty = it.optInt("qty", 1),
                unitToman = it.optInt("unit_toman", 0),
                totalToman = it.optInt("total_toman", 0),
                kind = it.optString("kind", "item")
            )
        }
        return Invoice(
            purchaseId = d.optString("purchase_id", purchaseId),
            invoiceNumber = d.optString("invoice_number"),
            status = d.optString("status"),
            statusLabel = d.optString("status_label"),
            issuedAt = d.optString("issued_at"),
            subtotalToman = d.optInt("subtotal_toman", 0),
            discountToman = d.optInt("discount_toman", 0),
            totalToman = d.optInt("total_toman", 0),
            refNumber = d.optString("ref_number"),
            cardPan = d.optString("card_pan"),
            items = items
        )
    }

    private fun parsePurchase(o: JSONObject, fallbackPayUrl: String): Purchase = Purchase(
        purchaseId = o.optString("purchase_id"),
        sku = o.optString("sku"),
        amountToman = o.optInt("amount_toman", 0),
        grossToman = o.optInt("gross_toman", o.optInt("amount_toman", 0)),
        discountToman = o.optInt("discount_toman", 0),
        discountCode = o.optString("discount_code"),
        status = o.optString("status", "pending"),
        refNumber = o.optString("ref_number"),
        paymentUrl = o.optString("payment_url").ifBlank { fallbackPayUrl },
        createdAt = o.optString("created_at"),
        paidAt = o.optString("paid_at").takeIf { it.isNotBlank() && it != "null" },
        consumedAt = o.optString("consumed_at").takeIf { it.isNotBlank() && it != "null" },
        invoiceNumber = o.optString("invoice_number"),
        invoiceUrl = o.optString("invoice_url")
    )

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val root = rawCall(method, path, body)
        return root.optJSONObject("data") ?: JSONObject()
    }

    private fun rawCall(method: String, path: String, body: JSONObject?): JSONObject {
        require(paylineToken.startsWith("pl_")) { "توکن خط پرداخت فائری باید با pl_ آغاز شود." }
        val conn = (URL(marketBaseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Payline", paylineToken)
            setRequestProperty("User-Agent", "FaeriePaylineSDK-Android/1.0")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw PaylineException("پاسخ سرور معتبر نیست (HTTP $status).", status)
            if (status !in 200..299 || !json.optBoolean("success", false)) {
                throw PaylineException(
                    message = json.optString("message").ifBlank { "خطای ارتباط با درگاه فائری ($status)" },
                    httpCode = status,
                    errorCode = json.optString("code"),
                    requiresNationalId = json.optBoolean("requires_national_id", false)
                )
            }
            json
        } catch (e: PaylineException) {
            throw e
        } catch (e: Exception) {
            throw PaylineException("خطا در اتصال به درگاه فائری: ${e.localizedMessage ?: "عدم دسترسی به شبکه"}", 0, cause = e)
        } finally {
            conn.disconnect()
        }
    }
}
