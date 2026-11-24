using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace Faerie.Payline.DotNet
{
    /// <summary>
    /// Faerie Payline SDK — کلاینت رسمی C# برای .NET 8 / .NET MAUI / Xamarin.Android / ASP.NET Core.
    /// </summary>
    public sealed class FaeriePaylineClient : IDisposable
    {
        private readonly HttpClient _http;
        private readonly bool _ownsClient;

        public record Product(
            [property: JsonPropertyName("sku")] string Sku,
            [property: JsonPropertyName("title")] string Title,
            [property: JsonPropertyName("description")] string Description,
            [property: JsonPropertyName("type")] string Type,
            [property: JsonPropertyName("price_toman")] int PriceToman,
            [property: JsonPropertyName("custom_amount")] bool CustomAmount,
            [property: JsonPropertyName("min_toman")] int MinToman,
            [property: JsonPropertyName("max_toman")] int MaxToman
        );

        public record Purchase(
            [property: JsonPropertyName("purchase_id")] string PurchaseId,
            [property: JsonPropertyName("sku")] string Sku,
            [property: JsonPropertyName("amount_toman")] int AmountToman,
            [property: JsonPropertyName("gross_toman")] int GrossToman,
            [property: JsonPropertyName("discount_toman")] int DiscountToman,
            [property: JsonPropertyName("status")] string Status,
            [property: JsonPropertyName("ref_number")] string RefNumber,
            [property: JsonPropertyName("payment_url")] string PaymentUrl,
            [property: JsonPropertyName("invoice_number")] string InvoiceNumber
        )
        {
            public bool IsPaid => Status is "paid" or "consumed";
        }

        public FaeriePaylineClient(string paylineToken, string baseUrl = "https://faerie.ir", HttpClient? httpClient = null)
        {
            _ownsClient = httpClient is null;
            _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(20) };
            _http.BaseAddress = new Uri(baseUrl.TrimEnd('/') + "/");
            _http.DefaultRequestHeaders.Remove("X-Payline");
            _http.DefaultRequestHeaders.Add("X-Payline", paylineToken.Trim());
        }

        public async Task<IReadOnlyList<Product>> GetProductsAsync(CancellationToken ct = default)
        {
            using var doc = await SendAsync(HttpMethod.Get, "api/v1/iap/products", null, ct);
            var arr = doc.RootElement.GetProperty("data").GetProperty("products");
            return JsonSerializer.Deserialize<List<Product>>(arr.GetRawText()) ?? new List<Product>();
        }

        public async Task<Purchase> InitPurchaseAsync(
            string sku,
            string userId,
            string? mobile = null,
            string? nationalCode = null,
            string? discountCode = null,
            int? amountToman = null,
            string? returnUrl = null,
            CancellationToken ct = default)
        {
            var payload = new Dictionary<string, object?> { ["sku"] = sku, ["user_id"] = userId };
            if (!string.IsNullOrWhiteSpace(mobile)) payload["mobile"] = mobile;
            if (!string.IsNullOrWhiteSpace(nationalCode)) payload["national_code"] = nationalCode;
            if (!string.IsNullOrWhiteSpace(discountCode)) payload["discount_code"] = discountCode;
            if (amountToman is > 0) payload["amount"] = amountToman;
            if (!string.IsNullOrWhiteSpace(returnUrl)) payload["return_url"] = returnUrl;

            using var doc = await SendAsync(HttpMethod.Post, "api/v1/iap/init", payload, ct);
            var data = doc.RootElement.GetProperty("data");
            var pElem = data.TryGetProperty("purchase", out var inner) ? inner : data;
            var p = JsonSerializer.Deserialize<Purchase>(pElem.GetRawText())!;
            if (string.IsNullOrEmpty(p.PaymentUrl) && data.TryGetProperty("payment_url", out var u))
            {
                p = p with { PaymentUrl = u.GetString() ?? "" };
            }
            return p;
        }

        public async Task<Purchase> GetStatusAsync(string purchaseId, CancellationToken ct = default)
        {
            using var doc = await SendAsync(HttpMethod.Get, $"api/v1/iap/status/{Uri.EscapeDataString(purchaseId)}", null, ct);
            return JsonSerializer.Deserialize<Purchase>(doc.RootElement.GetProperty("data").GetRawText())!;
        }

        public async Task<Purchase> ConsumeAsync(string purchaseId, string userId, CancellationToken ct = default)
        {
            var body = new { purchase_id = purchaseId, user_id = userId };
            using var doc = await SendAsync(HttpMethod.Post, "api/v1/iap/consume", body, ct);
            return JsonSerializer.Deserialize<Purchase>(doc.RootElement.GetProperty("data").GetRawText())!;
        }

        private async Task<JsonDocument> SendAsync(HttpMethod method, string path, object? payload, CancellationToken ct)
        {
            using var req = new HttpRequestMessage(method, path);
            if (payload is not null)
            {
                req.Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
            }
            using var res = await _http.SendAsync(req, ct);
            var text = await res.Content.ReadAsStringAsync(ct);
            var doc = JsonDocument.Parse(text);
            if (!res.IsSuccessStatusCode || !doc.RootElement.TryGetProperty("success", out var ok) || !ok.GetBoolean())
            {
                var msg = doc.RootElement.TryGetProperty("message", out var m) ? m.GetString() : "خطای ارتباط با درگاه فائری";
                doc.Dispose();
                throw new InvalidOperationException(msg);
            }
            return doc;
        }

        public void Dispose()
        {
            if (_ownsClient) _http.Dispose();
        }
    }
}
