using UnityEngine;
using UnityEngine.Events;
using UnityEngine.UI;

namespace Faerie.Payline
{
    /// <summary>
    /// کامپوننت آمادهٔ دکمهٔ «پرداخت با فائری» برای Unity UI (uGUI).
    /// کافی است این اسکریپت را روی یک Button در Canvas بیندازید و SKU را در Inspector پر کنید!
    /// </summary>
    [RequireComponent(typeof(Button))]
    public class FaeriePayUnityButton : MonoBehaviour
    {
        [Tooltip("شناسهٔ محصول در پنل فائری (مثلاً gems_500)")]
        public string sku = "gems_500";

        [Tooltip("شناسهٔ بازیکن در بازی شما (اگر خالی باشد از SystemInfo.deviceUniqueIdentifier استفاده می‌شود)")]
        public string playerId = "";

        public UnityEvent<string, string> onPurchaseSucceeded; // (purchaseId, invoiceNumber)
        public UnityEvent<string> onPurchaseFailed;            // (errorMessage)

        private Button _btn;

        private void Awake()
        {
            _btn = GetComponent<Button>();
            _btn.onClick.AddListener(TriggerPayment);
        }

        public void TriggerPayment()
        {
            if (FaeriePaylineManager.Instance == null)
            {
                onPurchaseFailed?.Invoke("ابتدا کامپوننت FaeriePaylineManager را در صحنه قرار دهید.");
                return;
            }
            string uid = string.IsNullOrWhiteSpace(playerId) ? SystemInfo.deviceUniqueIdentifier : playerId;
            _btn.interactable = false;

            FaeriePaylineManager.Instance.BuyProduct(
                sku: sku,
                playerUserId: uid,
                onSuccess: purchase =>
                {
                    _btn.interactable = true;
                    onPurchaseSucceeded?.Invoke(purchase.purchase_id, purchase.invoice_number);
                },
                onError: (msg, needsNat) =>
                {
                    _btn.interactable = true;
                    onPurchaseFailed?.Invoke(msg);
                }
            );
        }
    }
}
