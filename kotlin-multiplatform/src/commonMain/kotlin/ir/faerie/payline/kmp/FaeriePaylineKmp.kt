package ir.faerie.payline.kmp

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Faerie Payline SDK — Kotlin Multiplatform (Android, Desktop JVM, iOS, Web/Wasm).
 */
class FaeriePaylineKmp(
    private val httpClient: HttpClient,
    baseUrl: String = "https://faerie.ir",
    private val paylineToken: String
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Product(
        val sku: String,
        val title: String,
        val description: String = "",
        val type: String = "consumable",
        @SerialName("price_toman") val priceToman: Int = 0,
        @SerialName("custom_amount") val customAmount: Boolean = false,
        @SerialName("min_toman") val minToman: Int = 0,
        @SerialName("max_toman") val maxToman: Int = 0
    )

    @Serializable
    data class Purchase(
        @SerialName("purchase_id") val purchaseId: String,
        val sku: String,
        @SerialName("amount_toman") val amountToman: Int = 0,
        @SerialName("gross_toman") val grossToman: Int = 0,
        @SerialName("discount_toman") val discountToman: Int = 0,
        val status: String = "pending",
        @SerialName("ref_number") val refNumber: String = "",
        @SerialName("payment_url") val paymentUrl: String = "",
        @SerialName("invoice_number") val invoiceNumber: String = ""
    ) {
        val isPaid: Boolean get() = status == "paid" || status == "consumed"
    }

    suspend fun getProducts(): List<Product> {
        val data = call("GET", "/api/v1/iap/products")
        val arr = data["products"]?.jsonArray ?: JsonArray(emptyList())
        return arr.map { json.decodeFromJsonElement(Product.serializer(), it) }
    }

    suspend fun initPurchase(
        sku: String,
        userId: String,
        mobile: String? = null,
        nationalCode: String? = null,
        discountCode: String? = null,
        amountToman: Int? = null,
        returnUrl: String? = null
    ): Purchase {
        val payload = buildJsonObject {
            put("sku", sku)
            put("user_id", userId)
            mobile?.takeIf { it.isNotBlank() }?.let { put("mobile", it) }
            nationalCode?.takeIf { it.isNotBlank() }?.let { put("national_code", it) }
            discountCode?.takeIf { it.isNotBlank() }?.let { put("discount_code", it) }
            amountToman?.takeIf { it > 0 }?.let { put("amount", it) }
            returnUrl?.takeIf { it.isNotBlank() }?.let { put("return_url", it) }
        }
        val data = call("POST", "/api/v1/iap/init", payload)
        val pElem = data["purchase"] ?: data
        val parsed = json.decodeFromJsonElement(Purchase.serializer(), pElem)
        val payUrl = data["payment_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return if (parsed.paymentUrl.isBlank() && payUrl.isNotBlank()) parsed.copy(paymentUrl = payUrl) else parsed
    }

    suspend fun getStatus(purchaseId: String): Purchase {
        val data = call("GET", "/api/v1/iap/status/$purchaseId")
        return json.decodeFromJsonElement(Purchase.serializer(), data)
    }

    suspend fun consume(purchaseId: String, userId: String): Purchase {
        val payload = buildJsonObject {
            put("purchase_id", purchaseId)
            put("user_id", userId)
        }
        val data = call("POST", "/api/v1/iap/consume", payload)
        return json.decodeFromJsonElement(Purchase.serializer(), data)
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject {
        val resp = httpClient.request(base + path) {
            this.method = io.ktor.http.HttpMethod.parse(method)
            header("X-Payline", paylineToken)
            header("Accept", "application/json")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val ok = root["success"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!ok) {
            val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "خطا در ارتباط با درگاه فائری"
            throw IllegalStateException(msg)
        }
        return root["data"]?.jsonObject ?: JsonObject(emptyMap())
    }
}
