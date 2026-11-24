package ir.faerie.payline.java;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Faerie Payline SDK — نسخهٔ خالص Java برای پروژه‌های کلاسیک Android Studio (XML / Views).
 *
 * شامل:
 *  1) کلاینت غیرمسدودکننده با Thread Pool داخلی و بازگشت نتیجه روی Main Thread
 *  2) ویجت دکمهٔ آمادهٔ «پرداخت با فائری» (createPayButton) قابل افزودن مستقیم به هر Layout
 *  3) متد کمکی باز کردن برنامه فائری یا صفحهٔ واسط وب (openGateway)
 */
public final class FaeriePayline {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message, int httpCode, boolean requiresNationalId);
    }

    public static final class Product {
        public final String sku;
        public final String title;
        public final String description;
        public final String type;
        public final int priceToman;
        public final boolean customAmount;
        public final int minToman;
        public final int maxToman;

        public Product(String sku, String title, String description, String type,
                       int priceToman, boolean customAmount, int minToman, int maxToman) {
            this.sku = sku;
            this.title = title;
            this.description = description;
            this.type = type;
            this.priceToman = priceToman;
            this.customAmount = customAmount;
            this.minToman = minToman;
            this.maxToman = maxToman;
        }

        public boolean isConsumable() {
            return "consumable".equals(type);
        }
    }

    public static final class Purchase {
        public final String purchaseId;
        public final String sku;
        public final int amountToman;
        public final int grossToman;
        public final int discountToman;
        public final String status;
        public final String refNumber;
        public final String paymentUrl;
        public final String invoiceNumber;

        public Purchase(String purchaseId, String sku, int amountToman, int grossToman,
                        int discountToman, String status, String refNumber,
                        String paymentUrl, String invoiceNumber) {
            this.purchaseId = purchaseId;
            this.sku = sku;
            this.amountToman = amountToman;
            this.grossToman = grossToman;
            this.discountToman = discountToman;
            this.status = status;
            this.refNumber = refNumber;
            this.paymentUrl = paymentUrl;
            this.invoiceNumber = invoiceNumber;
        }

        public boolean isPaid() {
            return "paid".equals(status) || "consumed".equals(status);
        }
    }

    private final String baseUrl;
    private final String paylineToken;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FaeriePayline(String baseUrl, String paylineToken) {
        String clean = (baseUrl == null || baseUrl.trim().isEmpty()) ? "https://faerie.ir" : baseUrl.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        this.baseUrl = clean;
        this.paylineToken = paylineToken == null ? "" : paylineToken.trim();
    }

    /** ۱. دریافت فهرست محصولات برنامه */
    public void getProducts(final Callback<List<Product>> callback) {
        executor.execute(() -> {
            try {
                JSONObject root = request("GET", "/api/v1/iap/products", null);
                JSONObject data = root.optJSONObject("data");
                JSONArray arr = data != null ? data.optJSONArray("products") : new JSONArray();
                List<Product> list = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new Product(
                                o.optString("sku"),
                                o.optString("title"),
                                o.optString("description"),
                                o.optString("type", "consumable"),
                                o.optInt("price_toman", 0),
                                o.optBoolean("custom_amount", false),
                                o.optInt("min_toman", 0),
                                o.optInt("max_toman", 0)
                        ));
                    }
                }
                postSuccess(callback, list);
            } catch (ApiError e) {
                postError(callback, e);
            }
        });
    }

    /** ۲. شروع تراکنش خرید */
    public void initPurchase(final String sku, final String userId, final String mobile,
                             final String nationalCode, final String discountCode,
                             final int customAmountToman, final String returnUrl,
                             final Callback<Purchase> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sku", sku);
                body.put("user_id", userId);
                if (mobile != null && !mobile.trim().isEmpty()) body.put("mobile", mobile.trim());
                if (nationalCode != null && !nationalCode.trim().isEmpty()) body.put("national_code", nationalCode.trim());
                if (discountCode != null && !discountCode.trim().isEmpty()) body.put("discount_code", discountCode.trim());
                if (customAmountToman > 0) body.put("amount", customAmountToman);
                if (returnUrl != null && !returnUrl.trim().isEmpty()) body.put("return_url", returnUrl.trim());

                JSONObject root = request("POST", "/api/v1/iap/init", body);
                JSONObject data = root.optJSONObject("data");
                JSONObject pObj = data != null && data.optJSONObject("purchase") != null
                        ? data.optJSONObject("purchase") : (data != null ? data : new JSONObject());
                String payUrl = data != null ? data.optString("payment_url") : "";
                postSuccess(callback, parsePurchase(pObj, payUrl));
            } catch (ApiError e) {
                postError(callback, e);
            } catch (Exception e) {
                postError(callback, new ApiError(e.getMessage(), 0, false));
            }
        });
    }

    /** ۳. استعلام وضعیت پرداخت از سرور (تنها منبع حقیقت پس از بازگشت کاربر) */
    public void checkStatus(final String purchaseId, final Callback<Purchase> callback) {
        executor.execute(() -> {
            try {
                String enc = URLEncoder.encode(purchaseId, "UTF-8");
                JSONObject root = request("GET", "/api/v1/iap/status/" + enc, null);
                JSONObject data = root.optJSONObject("data");
                postSuccess(callback, parsePurchase(data != null ? data : new JSONObject(), ""));
            } catch (ApiError e) {
                postError(callback, e);
            } catch (Exception e) {
                postError(callback, new ApiError(e.getMessage(), 0, false));
            }
        });
    }

    /** ۴. ثبت مصرف و تحویل کالای مصرفی */
    public void consume(final String purchaseId, final String userId, final Callback<Purchase> callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("purchase_id", purchaseId);
                body.put("user_id", userId);
                JSONObject root = request("POST", "/api/v1/iap/consume", body);
                JSONObject data = root.optJSONObject("data");
                postSuccess(callback, parsePurchase(data != null ? data : new JSONObject(), ""));
            } catch (ApiError e) {
                postError(callback, e);
            } catch (Exception e) {
                postError(callback, new ApiError(e.getMessage(), 0, false));
            }
        });
    }

    /** ۵. دریافت JSON کامل فاکتور رسمی */
    public void getInvoiceJson(final String purchaseId, final Callback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String enc = URLEncoder.encode(purchaseId, "UTF-8");
                JSONObject root = request("GET", "/api/v1/iap/invoice/" + enc, null);
                postSuccess(callback, root.optJSONObject("data"));
            } catch (ApiError e) {
                postError(callback, e);
            } catch (Exception e) {
                postError(callback, new ApiError(e.getMessage(), 0, false));
            }
        });
    }

    /** باز کردن برنامه فائری یا صفحهٔ وب واسط پرداخت */
    public static void openGateway(Context context, Purchase purchase) {
        try {
            Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse("faerie://pay/" + Uri.encode(purchase.purchaseId)));
            deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(deep);
        } catch (Exception ignored) {
            if (purchase.paymentUrl != null && !purchase.paymentUrl.isEmpty()) {
                Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(purchase.paymentUrl));
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(web);
            }
        }
    }

    /** ساخت ویجت دکمهٔ استاندارد «پرداخت با فائری» بدون نیاز به فایل XML */
    public static LinearLayout createPayButton(Context ctx, String label, Runnable onClick) {
        float density = ctx.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        int padH = (int) (18 * density);
        int padV = (int) (12 * density);
        box.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#5B5CE6"), Color.parseColor("#7C4DFF")}
        );
        bg.setCornerRadius(14 * density);
        box.setBackground(bg);

        TextView tv = new TextView(ctx);
        tv.setText(label == null || label.isEmpty() ? "پرداخت با فائری" : label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.getPaint().setFakeBoldText(true);
        box.addView(tv);
        box.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        return box;
    }

    private Purchase parsePurchase(JSONObject o, String fallbackUrl) {
        String url = o.optString("payment_url");
        if (url.isEmpty()) url = fallbackUrl;
        return new Purchase(
                o.optString("purchase_id"),
                o.optString("sku"),
                o.optInt("amount_toman", 0),
                o.optInt("gross_toman", o.optInt("amount_toman", 0)),
                o.optInt("discount_toman", 0),
                o.optString("status", "pending"),
                o.optString("ref_number"),
                url,
                o.optString("invoice_number")
        );
    }

    private JSONObject request(String method, String path, JSONObject body) throws ApiError {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Payline", paylineToken);
            conn.setRequestProperty("User-Agent", "FaeriePaylineSDK-Java/1.0");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
            }
            JSONObject json = new JSONObject(sb.toString());
            if (code < 200 || code >= 300 || !json.optBoolean("success", false)) {
                throw new ApiError(
                        json.optString("message", "خطای ارتباط با درگاه فائری"),
                        code,
                        json.optBoolean("requires_national_id", false)
                );
            }
            return json;
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            throw new ApiError("خطا در اتصال به شبکه: " + e.getMessage(), 0, false);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private <T> void postSuccess(Callback<T> cb, T data) {
        mainHandler.post(() -> cb.onSuccess(data));
    }

    private <T> void postError(Callback<T> cb, ApiError err) {
        mainHandler.post(() -> cb.onError(err.getMessage(), err.httpCode, err.requiresNationalId));
    }

    private static final class ApiError extends Exception {
        final int httpCode;
        final boolean requiresNationalId;

        ApiError(String msg, int httpCode, boolean requiresNationalId) {
            super(msg);
            this.httpCode = httpCode;
            this.requiresNationalId = requiresNationalId;
        }
    }
}
