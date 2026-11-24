package ir.faerie.payline.webview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * پل امن و کامل اندروید برای برنامه‌هایی که با WebView ساخته شده‌اند!
 *
 * دو مشکل بزرگ برنامه‌های WebView را به‌صورت یکجا حل می‌کند:
 * ۱) جلوگیری از خطای ERR_UNKNOWN_URL_SCHEME روی لینک‌های `faerie://pay/...` و باز کردن مستقیم برنامه فائری؛
 * ۲) نگه‌داری امن کلید `pl_...` در کد Kotlin اندروید (به‌جای لو رفتن در سورس HTML) و ارسال رویداد
 *    `window.dispatchEvent(new CustomEvent('faerie:purchase', { detail }))` به صفحهٔ وب به محض بازگشت کاربر!
 */
class FaerieWebViewBridge(
    private val activity: Activity,
    private val webView: WebView,
    private val paylineToken: String,
    private val baseUrl: String = "https://faerie.ir",
    private val autoConsume: Boolean = true
) {
    private val io = Executors.newSingleThreadExecutor()

    @Volatile
    private var pendingPurchaseId: String? = null

    @Volatile
    private var pendingUserId: String = "webview_user"

    /** اتصال یک‌خطی پل به WebView */
    fun attach() {
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(JsApi(), "FaerieNativeBridge")
        webView.webViewClient = createInterceptingWebViewClient()
    }

    /** این متد را در `Activity.onResume()` صدا بزنید تا به محض بازگشت کاربر از برنامه فائری، خرید بررسی و به وب اطلاع داده شود. */
    fun onActivityResume() {
        val pid = pendingPurchaseId ?: return
        pendingPurchaseId = null
        io.execute {
            runCatching {
                val statusUrl = "${baseUrl.trimEnd('/')}/api/v1/iap/status/${URLEncoder.encode(pid, "UTF-8")}"
                var data = requestJson("GET", statusUrl, null).getJSONObject("data")
                val st = data.optString("status")
                if (autoConsume && st == "paid") {
                    val consumeBody = JSONObject()
                        .put("purchase_id", pid)
                        .put("user_id", pendingUserId)
                    data = requestJson("POST", "${baseUrl.trimEnd('/')}/api/v1/iap/consume", consumeBody)
                        .optJSONObject("data") ?: data
                }
                val jsPayload = JSONObject.quote(data.toString())
                activity.runOnUiThread {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('faerie:purchase',{detail:JSON.parse($jsPayload)}));",
                        null
                    )
                }
            }
        }
    }

    /** WebViewClient هوشمند که لینک‌های `faerie://` و `/pay/fp_...` را به برنامه فائری یا مرورگر سیستم هدایت می‌کند */
    fun createInterceptingWebViewClient(delegate: WebViewClient? = null): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase() ?: ""
                val isFaeriePayPage = (scheme == "http" || scheme == "https") &&
                    uri.path?.startsWith("/pay/fp_") == true

                if (scheme == "faerie" || isFaeriePayPage) {
                    launchExternal(uri)
                    return true
                }
                return delegate?.shouldOverrideUrlLoading(view, request) ?: false
            }
        }
    }

    private fun launchExternal(uri: Uri) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            if (uri.scheme == "faerie") {
                val uid = uri.lastPathSegment ?: return
                val fallback = Uri.parse("${baseUrl.trimEnd('/')}/pay/$uid")
                activity.startActivity(Intent(Intent.ACTION_VIEW, fallback))
            }
        }
    }

    inner class JsApi {
        @JavascriptInterface
        fun startPurchase(sku: String, userId: String, extrasJson: String?): String {
            val extras = runCatching { JSONObject(extrasJson ?: "{}") }.getOrDefault(JSONObject())
            val payload = JSONObject()
                .put("sku", sku)
                .put("user_id", userId.ifBlank { "webview_user" })
            extras.keys().forEach { k ->
                val v = extras.opt(k)
                if (v != null && v.toString().isNotBlank() && v.toString() != "0") {
                    payload.put(k, v)
                }
            }
            val root = requestJson("POST", "${baseUrl.trimEnd('/')}/api/v1/iap/init", payload)
            val data = root.getJSONObject("data")
            val purchase = data.optJSONObject("purchase") ?: data
            val pid = purchase.getString("purchase_id")
            val payUrl = data.optString("payment_url").ifBlank { purchase.optString("payment_url") }

            pendingPurchaseId = pid
            pendingUserId = userId.ifBlank { "webview_user" }

            activity.runOnUiThread {
                val deep = Uri.parse("faerie://pay/${Uri.encode(pid)}")
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, deep))
                } catch (_: ActivityNotFoundException) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(payUrl)))
                }
            }
            return purchase.toString()
        }
    }

    private fun requestJson(method: String, url: String, body: JSONObject?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Payline", paylineToken)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        if (body != null) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = JSONObject(text.ifBlank { "{}" })
        if (conn.responseCode !in 200..299 || !json.optBoolean("success", false)) {
            throw IllegalStateException(json.optString("message", "خطای درگاه فائری"))
        }
        return json
    }
}
