<?php

declare(strict_types=1);

namespace Faerie\Payline;

use RuntimeException;

/**
 * کلاینت رسمی PHP 8+ برای خط پرداخت فائری (Faerie Payline).
 * بدون هیچ وابستگی خارجی — قابل استفاده در پروژه‌های PHP خام، Laravel، Symfony و Yii.
 */
final class FaeriePayline
{
    private string $baseUrl;
    private string $paylineToken;
    private int $timeoutSeconds;

    public function __construct(string $paylineToken, string $baseUrl = 'https://faerie.ir', int $timeoutSeconds = 20)
    {
        $this->paylineToken   = trim($paylineToken);
        $this->baseUrl        = rtrim(trim($baseUrl), '/');
        $this->timeoutSeconds = max(5, $timeoutSeconds);
    }

    /** ۱) دریافت فهرست آیتم‌های تعریف‌شده در پنل توسعه‌دهندگان */
    public function getProducts(): array
    {
        $res = $this->request('GET', '/api/v1/iap/products');
        return $res['data']['products'] ?? [];
    }

    /**
     * ۲) ایجاد تراکنش جدید و دریافت لینک پرداخت (payment_url)
     *
     * @param array{
     *   sku: string,
     *   user_id: string,
     *   amount?: int,
     *   mobile?: string,
     *   national_code?: string,
     *   discount_code?: string,
     *   return_url?: string
     * } $params
     */
    public function initPurchase(array $params): array
    {
        $res      = $this->request('POST', '/api/v1/iap/init', $params);
        $data     = $res['data'] ?? [];
        $purchase = $data['purchase'] ?? $data;
        if (empty($purchase['payment_url']) && !empty($data['payment_url'])) {
            $purchase['payment_url'] = $data['payment_url'];
        }
        return $purchase;
    }

    /** ۳) استعلام قطعی وضعیت پرداخت از سرور فائری (تنها معیار معتبر تحویل آیتم/سفارش) */
    public function getStatus(string $purchaseId): array
    {
        $res = $this->request('GET', '/api/v1/iap/status/' . rawurlencode($purchaseId));
        return $res['data'] ?? [];
    }

    /** ۴) مصرف آیتم مصرفی (Consumable) پس از تحویل به کاربر — عملیات Idempotent */
    public function consume(string $purchaseId, string $userId): array
    {
        $res = $this->request('POST', '/api/v1/iap/consume', [
            'purchase_id' => $purchaseId,
            'user_id'     => $userId,
        ]);
        return $res['data'] ?? [];
    }

    /** ۵) بررسی و مصرف یکجای تراکنش هنگام بازگشت از درگاه (مناسب Callback در وب و لاراول) */
    public function verifyAndConsume(string $purchaseId, string $userId, bool $autoConsume = true): array
    {
        $tx     = $this->getStatus($purchaseId);
        $status = (string)($tx['status'] ?? '');
        if (!in_array($status, ['paid', 'consumed'], true)) {
            throw new FaeriePaylineException('تراکنش پرداخت نشده است (وضعیت: ' . $status . ')', 402);
        }
        if ($autoConsume && $status === 'paid') {
            $tx = $this->consume($purchaseId, $userId);
        }
        return $tx;
    }

    /** ۶) تاریخچهٔ خریدهای یک کاربر */
    public function listUserPurchases(string $userId): array
    {
        $res = $this->request('GET', '/api/v1/iap/purchases?user_id=' . rawurlencode($userId));
        return $res['data'] ?? [];
    }

    /** ۷) دریافت فاکتور رسمی صادرشده توسط مارکت فائری */
    public function getInvoice(string $purchaseId): array
    {
        $res = $this->request('GET', '/api/v1/iap/invoice/' . rawurlencode($purchaseId));
        return $res['data'] ?? [];
    }

    private function request(string $method, string $path, ?array $body = null): array
    {
        $ch = curl_init($this->baseUrl . $path);
        if ($ch === false) {
            throw new RuntimeException('امکان راه‌اندازی cURL وجود ندارد.');
        }

        $headers = [
            'Accept: application/json',
            'X-Payline: ' . $this->paylineToken,
        ];

        $opts = [
            CURLOPT_CUSTOMREQUEST  => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->timeoutSeconds,
            CURLOPT_CONNECTTIMEOUT => 10,
        ];

        if ($body !== null && $method !== 'GET') {
            $headers[]                = 'Content-Type: application/json; charset=utf-8';
            $opts[CURLOPT_POSTFIELDS] = json_encode($body, JSON_UNESCAPED_UNICODE);
        }

        $opts[CURLOPT_HTTPHEADER] = $headers;
        curl_setopt_array($ch, $opts);

        $raw  = curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err  = curl_error($ch);
        curl_close($ch);

        if ($raw === false) {
            throw new FaeriePaylineException('خطای شبکه در اتصال به فائری: ' . $err, 0);
        }

        $decoded = json_decode((string)$raw, true);
        if (!is_array($decoded)) {
            throw new FaeriePaylineException('پاسخ نامعتبر از سرور فائری (' . $code . ')', $code);
        }

        if ($code < 200 || $code >= 300 || empty($decoded['success'])) {
            throw new FaeriePaylineException(
                (string)($decoded['message'] ?? 'خطای درگاه فائری'),
                $code,
                (string)($decoded['code'] ?? ''),
                !empty($decoded['requires_national_id'])
            );
        }

        return $decoded;
    }
}

final class FaeriePaylineException extends RuntimeException
{
    public function __construct(
        string $message,
        int $statusCode = 0,
        public readonly string $errorCode = '',
        public readonly bool $requiresNationalId = false
    ) {
        parent::__construct($message, $statusCode);
    }
}
