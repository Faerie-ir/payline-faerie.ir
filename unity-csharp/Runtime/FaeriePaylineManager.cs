using System;
using System.Collections;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;

namespace Faerie.Payline
{
    /// <summary>
    /// Faerie Payline SDK for Unity (مشابه Bazaar IAP در یونیتی، بدون نیاز به پلاگین جاوا/AAR خارجی).
    /// کافی است این کامپوننت را روی یک GameObject در صحنه بگذارید یا از FaeriePaylineManager.Instance استفاده کنید.
    /// به‌محض بازگشت بازیکن از برنامه فائری یا مرورگر به بازی (OnApplicationFocus)، پرداخت خودکار بررسی و تحویل می‌شود.
    /// </summary>
    public class FaeriePaylineManager : MonoBehaviour
    {
        public static FaeriePaylineManager Instance { get; private set; }

        [Header("Faerie Payline Configuration")]
        [Tooltip("آدرس پایه مارکت فائری")]
        public string baseUrl = "https://faerie.ir";

        [Tooltip("کد خط پرداخت برنامه (با پیشوند pl_) از پنل توسعه‌دهندگان فائری")]
        public string paylineToken = "pl_YOUR_PAYLINE_TOKEN";

        [Tooltip("آیا پس از پرداخت موفق، آیتم مصرفی خودکار consume شود؟")]
        public bool autoConsume = true;

        [Serializable]
        public class ProductListWrapper
        {
            public bool success;
            public string message;
            public ProductListData data;
        }

        [Serializable]
        public class ProductListData
        {
            public string app;
            public Product[] products;
        }

        [Serializable]
        public class Product
        {
            public string sku;
            public string title;
            public string description;
            public string type;
            public int price_toman;
            public bool custom_amount;
            public int min_toman;
            public int max_toman;
        }

        [Serializable]
        public class PurchaseInitResponse
        {
            public bool success;
            public string message;
            public string code;
            public bool requires_national_id;
            public PurchaseInitData data;
        }

        [Serializable]
        public class PurchaseInitData
        {
            public Purchase purchase;
            public string payment_url;
        }

        [Serializable]
        public class PurchaseStatusResponse
        {
            public bool success;
            public string message;
            public Purchase data;
        }

        [Serializable]
        public class Purchase
        {
            public string purchase_id;
            public string sku;
            public int amount_toman;
            public int gross_toman;
            public int discount_toman;
            public string status;
            public string ref_number;
            public string payment_url;
            public string invoice_number;
            public string invoice_url;

            public bool IsPaid => status == "paid" || status == "consumed";
        }

        [Serializable]
        private class InitPayload
        {
            public string sku;
            public string user_id;
            public string mobile;
            public string national_code;
            public string discount_code;
            public int amount;
            public string return_url;
        }

        [Serializable]
        private class ConsumePayload
        {
            public string purchase_id;
            public string user_id;
        }

        private string _pendingPurchaseId;
        private string _pendingUserId;
        private Action<Purchase> _onPurchaseSuccess;
        private Action<string, bool> _onPurchaseError;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        private void OnApplicationFocus(bool hasFocus)
        {
            if (hasFocus && !string.IsNullOrEmpty(_pendingPurchaseId))
            {
                string pid = _pendingPurchaseId;
                _pendingPurchaseId = null;
                StartCoroutine(VerifyAndDeliverCoroutine(pid, _pendingUserId));
            }
        }

        /// <summary>۱) دریافت فهرست آیتم‌های فروشگاه از سرور فائری</summary>
        public void FetchProducts(Action<Product[]> onLoaded, Action<string> onError = null)
        {
            StartCoroutine(FetchProductsCoroutine(onLoaded, onError));
        }

        /// <summary>۲) شروع خرید آیتم و باز کردن خودکار برنامه فائری یا درگاه وب</summary>
        public void BuyProduct(
            string sku,
            string playerUserId,
            Action<Purchase> onSuccess,
            Action<string, bool> onError = null,
            string mobile = "",
            string nationalCode = "",
            string discountCode = "",
            int customAmountToman = 0,
            string returnDeepLink = "")
        {
            _onPurchaseSuccess = onSuccess;
            _onPurchaseError = onError;
            _pendingUserId = playerUserId;
            StartCoroutine(InitPurchaseCoroutine(sku, playerUserId, mobile, nationalCode, discountCode, customAmountToman, returnDeepLink));
        }

        /// <summary>۳) بررسی دستی وضعیت یک خرید (مثلاً برای بازیابی خریدهای ناتمام هنگام ورود به بازی)</summary>
        public void VerifyPurchase(string purchaseId, string playerUserId, Action<Purchase> onSuccess, Action<string, bool> onError = null)
        {
            _onPurchaseSuccess = onSuccess;
            _onPurchaseError = onError;
            StartCoroutine(VerifyAndDeliverCoroutine(purchaseId, playerUserId));
        }

        private IEnumerator FetchProductsCoroutine(Action<Product[]> onLoaded, Action<string> onError)
        {
            using (UnityWebRequest req = BuildRequest("GET", "/api/v1/iap/products", null))
            {
                yield return req.SendWebRequest();
                if (req.result != UnityWebRequest.Result.Success)
                {
                    onError?.Invoke(ExtractErrorMessage(req));
                    yield break;
                }
                var parsed = JsonUtility.FromJson<ProductListWrapper>(req.downloadHandler.text);
                if (parsed == null || !parsed.success)
                {
                    onError?.Invoke(parsed?.message ?? "خطا در خواندن محصولات از فائری");
                    yield break;
                }
                onLoaded?.Invoke(parsed.data?.products ?? Array.Empty<Product>());
            }
        }

        private IEnumerator InitPurchaseCoroutine(
            string sku, string userId, string mobile, string nationalCode,
            string discountCode, int customAmount, string returnUrl)
        {
            var payload = new InitPayload
            {
                sku = sku,
                user_id = userId,
                mobile = mobile,
                national_code = nationalCode,
                discount_code = discountCode,
                amount = customAmount,
                return_url = returnUrl
            };
            string json = JsonUtility.ToJson(payload);

            using (UnityWebRequest req = BuildRequest("POST", "/api/v1/iap/init", json))
            {
                yield return req.SendWebRequest();
                string text = req.downloadHandler != null ? req.downloadHandler.text : "";
                var parsed = !string.IsNullOrEmpty(text) ? JsonUtility.FromJson<PurchaseInitResponse>(text) : null;

                if (req.result != UnityWebRequest.Result.Success || parsed == null || !parsed.success)
                {
                    string msg = parsed != null && !string.IsNullOrEmpty(parsed.message) ? parsed.message : req.error;
                    bool needsNat = parsed != null && parsed.requires_national_id;
                    _onPurchaseError?.Invoke(msg, needsNat);
                    yield break;
                }

                Purchase p = parsed.data.purchase;
                string url = !string.IsNullOrEmpty(parsed.data.payment_url) ? parsed.data.payment_url : p.payment_url;
                _pendingPurchaseId = p.purchase_id;
                Application.OpenURL(url);
            }
        }

        private IEnumerator VerifyAndDeliverCoroutine(string purchaseId, string userId)
        {
            string path = "/api/v1/iap/status/" + UnityWebRequest.EscapeURL(purchaseId);
            using (UnityWebRequest req = BuildRequest("GET", path, null))
            {
                yield return req.SendWebRequest();
                var parsed = !string.IsNullOrEmpty(req.downloadHandler?.text)
                    ? JsonUtility.FromJson<PurchaseStatusResponse>(req.downloadHandler.text) : null;

                if (req.result != UnityWebRequest.Result.Success || parsed == null || !parsed.success || parsed.data == null)
                {
                    _onPurchaseError?.Invoke(parsed?.message ?? "تأیید تراکنش از سرور فائری ناموفق بود.", false);
                    yield break;
                }

                Purchase p = parsed.data;
                if (!p.IsPaid)
                {
                    _onPurchaseError?.Invoke("پرداخت تکمیل نشده است (وضعیت: " + p.status + ").", false);
                    yield break;
                }

                if (autoConsume && p.status == "paid")
                {
                    string consumeJson = JsonUtility.ToJson(new ConsumePayload { purchase_id = purchaseId, user_id = userId });
                    using (UnityWebRequest cReq = BuildRequest("POST", "/api/v1/iap/consume", consumeJson))
                    {
                        yield return cReq.SendWebRequest();
                        if (cReq.result == UnityWebRequest.Result.Success)
                        {
                            var consumed = JsonUtility.FromJson<PurchaseStatusResponse>(cReq.downloadHandler.text);
                            if (consumed != null && consumed.success && consumed.data != null)
                            {
                                p = consumed.data;
                            }
                        }
                    }
                }

                _onPurchaseSuccess?.Invoke(p);
            }
        }

        private UnityWebRequest BuildRequest(string method, string path, string jsonBody)
        {
            string cleanBase = string.IsNullOrWhiteSpace(baseUrl) ? "https://faerie.ir" : baseUrl.Trim().TrimEnd('/');
            var req = new UnityWebRequest(cleanBase + path, method);
            req.downloadHandler = new DownloadHandlerBuffer();
            req.timeout = 20;
            req.SetRequestHeader("Accept", "application/json");
            req.SetRequestHeader("X-Payline", paylineToken.Trim());
            if (!string.IsNullOrEmpty(jsonBody))
            {
                byte[] bytes = Encoding.UTF8.GetBytes(jsonBody);
                req.uploadHandler = new UploadHandlerRaw(bytes);
                req.SetRequestHeader("Content-Type", "application/json; charset=utf-8");
            }
            return req;
        }

        private static string ExtractErrorMessage(UnityWebRequest req)
        {
            if (req.downloadHandler != null && !string.IsNullOrEmpty(req.downloadHandler.text))
            {
                var r = JsonUtility.FromJson<PurchaseInitResponse>(req.downloadHandler.text);
                if (r != null && !string.IsNullOrEmpty(r.message)) return r.message;
            }
            return req.error ?? "خطا در شبکه";
        }
    }
}
