<?php
if (!defined('ABSPATH')) {
    exit;
}

class WC_Gateway_Faerie_Payline extends WC_Payment_Gateway
{
    public string $payline_token = '';
    public string $custom_sku = 'wc_order';

    public function __construct()
    {
        $this->id                 = 'faerie_payline';
        $this->method_title       = 'درگاه پرداخت فائری (Faerie Payline)';
        $this->method_description = 'پرداخت امن شاپرکی از طریق خط پرداخت فائری با صدور خودکار فاکتور رسمی.';
        $this->has_fields         = true;

        $this->init_form_fields();
        $this->init_settings();

        $globalCfg           = Faerie_Payline_WP_Plugin::settings();
        $this->title         = (string)$this->get_option('title', 'پرداخت امن با فائری');
        $this->description   = (string)$this->get_option('description', 'پرداخت آنلاین با کلیه کارت‌های عضو شتاب از طریق درگاه فائری.');
        $this->payline_token = (string)$this->get_option('payline_token', $globalCfg['payline_token']);
        $this->custom_sku    = (string)$this->get_option('custom_sku', $globalCfg['wc_custom_sku']);

        add_action('woocommerce_update_options_payment_gateways_' . $this->id, [$this, 'process_admin_options']);
        add_action('woocommerce_api_wc_gateway_faerie_payline', [$this, 'handle_callback']);
    }

    public function init_form_fields(): void
    {
        $this->form_fields = [
            'enabled' => [
                'title'   => 'فعال‌سازی',
                'type'    => 'checkbox',
                'label'   => 'فعال بودن درگاه پرداخت فائری در تسویه‌حساب ووکامرس',
                'default' => 'yes',
            ],
            'title' => [
                'title'   => 'عنوان درگاه برای مشتری',
                'type'    => 'text',
                'default' => 'پرداخت امن با فائری',
            ],
            'description' => [
                'title'   => 'توضیحات زیر درگاه',
                'type'    => 'textarea',
                'default' => 'پرداخت با کلیه کارت‌های بانکی عضو شتاب از طریق خط پرداخت فائری.',
            ],
            'payline_token' => [
                'title'       => 'کد خط پرداخت (Payline Token)',
                'type'        => 'text',
                'description' => 'کلید pl_... دریافتی از پنل توسعه‌دهندگان فائری.',
            ],
            'custom_sku' => [
                'title'       => 'SKU آیتم مبلغ دلخواه در پنل فائری',
                'type'        => 'text',
                'default'     => 'wc_order',
                'description' => 'شناسهٔ آیتمی که در پنل فائری با گزینهٔ «مبلغ دلخواه» تعریف کرده‌اید.',
            ],
        ];
    }

    public function payment_fields(): void
    {
        if ($this->description) {
            echo wpautop(wptexturize($this->description));
        }
        echo '<p class="form-row form-row-wide"><label>کد ملی خریدار (جهت استعلام شاپرکی فائری)</label>';
        echo '<input type="text" class="input-text" name="faerie_buyer_national_code" maxlength="10" dir="ltr" placeholder="کد ملی ۱۰ رقمی"></p>';
    }

    public function process_payment($order_id): array
    {
        $order    = wc_get_order($order_id);
        $currency = strtoupper($order->get_currency());
        $rawTotal = (float)$order->get_total();

        $amountToman = match ($currency) {
            'IRR'   => (int)round($rawTotal / 10),
            'IRHT'  => (int)round($rawTotal * 1000),
            default => (int)round($rawTotal),
        };

        $callbackUrl = add_query_arg('wc_order_id', $order_id, WC()->api_request_url('WC_Gateway_Faerie_Payline'));
        $national    = sanitize_text_field(wp_unslash($_POST['faerie_buyer_national_code'] ?? ''));
        $mobile      = (string)$order->get_billing_phone();
        $userId      = $order->get_user_id() ? 'wc_user_' . $order->get_user_id() : 'wc_order_' . $order_id;

        $payload = [
            'sku'        => $this->custom_sku,
            'user_id'    => $userId,
            'amount'     => $amountToman,
            'return_url' => $callbackUrl,
        ];
        if ($mobile !== '')   $payload['mobile'] = $mobile;
        if ($national !== '') $payload['national_code'] = $national;

        $res = Faerie_Payline_WP_Plugin::api_request('POST', '/api/v1/iap/init', $payload, $this->payline_token);
        if (empty($res['success']) || empty($res['data']['payment_url'])) {
            wc_add_notice((string)($res['message'] ?? 'خطا در اتصال به درگاه فائری.'), 'error');
            return ['result' => 'failure'];
        }

        $pid = (string)($res['data']['purchase']['purchase_id'] ?? '');
        $order->update_meta_data('_faerie_purchase_id', $pid);
        $order->update_meta_data('_faerie_user_id', $userId);
        $order->save();

        return [
            'result'   => 'success',
            'redirect' => $res['data']['payment_url'],
        ];
    }

    public function handle_callback(): void
    {
        $orderId = (int)($_GET['wc_order_id'] ?? 0);
        $order   = $orderId > 0 ? wc_get_order($orderId) : false;
        if (!$order) {
            wp_safe_redirect(wc_get_checkout_url());
            exit;
        }
        if ($order->is_paid()) {
            wp_safe_redirect($this->get_return_url($order));
            exit;
        }

        $pid = sanitize_text_field(wp_unslash($_GET['purchase_id'] ?? (string)$order->get_meta('_faerie_purchase_id')));
        if ($pid === '') {
            wc_add_notice('شناسه تراکنش فائری یافت نشد.', 'error');
            wp_safe_redirect(wc_get_checkout_url());
            exit;
        }

        // تأیید قطعی سمت سرور از API فائری
        $verify = Faerie_Payline_WP_Plugin::api_request('GET', '/api/v1/iap/status/' . rawurlencode($pid), null, $this->payline_token);
        $tx     = $verify['data'] ?? [];
        $status = (string)($tx['status'] ?? '');

        if (in_array($status, ['paid', 'consumed'], true)) {
            $uid = (string)($order->get_meta('_faerie_user_id') ?: ('wc_order_' . $orderId));
            if ($status === 'paid') {
                Faerie_Payline_WP_Plugin::api_request('POST', '/api/v1/iap/consume', [
                    'purchase_id' => $pid,
                    'user_id'     => $uid,
                ], $this->payline_token);
            }
            $ref = (string)($tx['ref_number'] ?? $pid);
            $inv = (string)($tx['invoice_number'] ?? '');
            $order->update_meta_data('_faerie_invoice_number', $inv);
            $order->add_order_note(sprintf('پرداخت فائری با موفقیت تأیید شد. شناسه: %s | مرجع بانکی: %s | فاکتور رسمی: %s', $pid, $ref, $inv));
            $order->payment_complete($ref);
            WC()->cart->empty_cart();
            wp_safe_redirect($this->get_return_url($order));
            exit;
        }

        $order->add_order_note('پرداخت فائری ناموفق یا لغو شد. وضعیت: ' . $status);
        wc_add_notice('پرداخت تکمیل نشد یا توسط خریدار لغو گردید.', 'error');
        wp_safe_redirect(wc_get_checkout_url());
        exit;
    }
}
