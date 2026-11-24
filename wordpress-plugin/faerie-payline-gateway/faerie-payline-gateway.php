<?php
/**
 * Plugin Name: درگاه پرداخت فائری (Faerie Payline) برای وردپرس و ووکامرس
 * Plugin URI: https://faerie.ir/developers/docs/payment
 * Description: اتصال مستقیم سایت‌های وردپرسی و فروشگاه‌های ووکامرس به خط پرداخت رسمی فائری (Faerie Payline) همراه با شورت‌کد مستقل [faerie_pay] و صدور فاکتور رسمی.
 * Version: 1.0.0
 * Author: Faerie Developer Platform
 * Author URI: https://faerie.ir
 * Text Domain: faerie-payline
 * Requires at least: 5.8
 * Requires PHP: 8.0
 */

if (!defined('ABSPATH')) {
    exit;
}

final class Faerie_Payline_WP_Plugin
{
    public const OPTION_KEY = 'faerie_payline_settings';

    public static function boot(): void
    {
        add_action('admin_menu', [self::class, 'register_settings_menu']);
        add_action('admin_init', [self::class, 'register_settings']);
        add_shortcode('faerie_pay', [self::class, 'render_shortcode']);
        add_action('init', [self::class, 'handle_shortcode_actions']);
        add_action('plugins_loaded', [self::class, 'init_woocommerce_gateway'], 20);
    }

    public static function settings(): array
    {
        $defaults = [
            'base_url'      => 'https://faerie.ir',
            'payline_token' => '',
            'wc_custom_sku' => 'wc_order',
        ];
        $saved = get_option(self::OPTION_KEY, []);
        return is_array($saved) ? array_merge($defaults, $saved) : $defaults;
    }

    public static function api_request(string $method, string $path, ?array $body = null, ?string $tokenOverride = null): array
    {
        $cfg   = self::settings();
        $base  = rtrim((string)$cfg['base_url'], '/');
        $token = trim((string)($tokenOverride ?? $cfg['payline_token']));

        $args = [
            'method'  => $method,
            'timeout' => 20,
            'headers' => [
                'Accept'       => 'application/json',
                'Content-Type' => 'application/json; charset=utf-8',
                'X-Payline'    => $token,
            ],
        ];
        if ($body !== null && $method !== 'GET') {
            $args['body'] = wp_json_encode($body);
        }

        $res = wp_remote_request($base . $path, $args);
        if (is_wp_error($res)) {
            return ['success' => false, 'message' => $res->get_error_message()];
        }
        $code = (int)wp_remote_retrieve_response_code($res);
        $json = json_decode((string)wp_remote_retrieve_body($res), true);
        if (!is_array($json)) {
            return ['success' => false, 'message' => 'پاسخ نامعتبر از سرور فائری (' . $code . ')'];
        }
        $json['_http_code'] = $code;
        return $json;
    }

    public static function register_settings_menu(): void
    {
        add_options_page(
            'تنظیمات درگاه فائری',
            'درگاه فائری (Payline)',
            'manage_options',
            'faerie-payline',
            [self::class, 'render_settings_page']
        );
    }

    public static function register_settings(): void
    {
        register_setting('faerie_payline_group', self::OPTION_KEY, [
            'sanitize_callback' => static function ($input) {
                return [
                    'base_url'      => esc_url_raw(trim((string)($input['base_url'] ?? 'https://faerie.ir'))),
                    'payline_token' => sanitize_text_field(trim((string)($input['payline_token'] ?? ''))),
                    'wc_custom_sku' => sanitize_key((string)($input['wc_custom_sku'] ?? 'wc_order')),
                ];
            },
        ]);
    }

    public static function render_settings_page(): void
    {
        $cfg = self::settings();
        ?>
        <div class="wrap" dir="rtl">
            <h1>تنظیمات خط پرداخت فائری (Faerie Payline)</h1>
            <p>کد خط پرداخت (با پیشوند <code>pl_</code>) را از پنل توسعه‌دهندگان فائری دریافت و در کادر زیر وارد کنید.</p>
            <form method="post" action="options.php">
                <?php settings_fields('faerie_payline_group'); ?>
                <table class="form-table">
                    <tr>
                        <th scope="row"><label for="fp_base_url">آدرس سرور فائری</label></th>
                        <td><input type="url" id="fp_base_url" name="<?php echo esc_attr(self::OPTION_KEY); ?>[base_url]" value="<?php echo esc_attr($cfg['base_url']); ?>" class="regular-text" dir="ltr"></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="fp_token">کد خط پرداخت (Payline Token)</label></th>
                        <td><input type="text" id="fp_token" name="<?php echo esc_attr(self::OPTION_KEY); ?>[payline_token]" value="<?php echo esc_attr($cfg['payline_token']); ?>" class="regular-text" dir="ltr" placeholder="pl_..."></td>
                    </tr>
                    <tr>
                        <th scope="row"><label for="fp_sku">SKU مبلغ دلخواه برای ووکامرس</label></th>
                        <td>
                            <input type="text" id="fp_sku" name="<?php echo esc_attr(self::OPTION_KEY); ?>[wc_custom_sku]" value="<?php echo esc_attr($cfg['wc_custom_sku']); ?>" class="regular-text" dir="ltr">
                            <p class="description">برای اتصال سبد خرید ووکامرس، در پنل فائری یک محصول با تیک «مبلغ دلخواه (Custom Amount)» بسازید و شناسه (SKU) آن را اینجا بنویسید.</p>
                        </td>
                    </tr>
                </table>
                <?php submit_button('ذخیره تنظیمات'); ?>
            </form>
            <hr>
            <h2>شورت‌کد آماده (بدون نیاز به ووکامرس)</h2>
            <p>در هر برگه یا نوشته می‌توانید از شورت‌کد زیر برای فروش مستقیم آیتم یا اشتراک استفاده کنید:</p>
            <code dir="ltr">[faerie_pay sku="vip_monthly" label="خرید اشتراک ویژه با فائری"]</code>
        </div>
        <?php
    }

    public static function handle_shortcode_actions(): void
    {
        if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && isset($_POST['faerie_pay_action']) && $_POST['faerie_pay_action'] === 'start') {
            if (!isset($_POST['_wpnonce']) || !wp_verify_nonce(sanitize_text_field(wp_unslash($_POST['_wpnonce'])), 'faerie_pay_start')) {
                wp_die('نشست امنیتی منقضی شده است.');
            }
            $sku      = sanitize_text_field(wp_unslash($_POST['faerie_sku'] ?? ''));
            $mobile   = sanitize_text_field(wp_unslash($_POST['faerie_mobile'] ?? ''));
            $national = sanitize_text_field(wp_unslash($_POST['faerie_national'] ?? ''));
            $discount = sanitize_text_field(wp_unslash($_POST['faerie_discount'] ?? ''));
            $userId   = is_user_logged_in() ? 'wp_user_' . get_current_user_id() : 'wp_guest_' . substr(md5((string)($_SERVER['REMOTE_ADDR'] ?? '')), 0, 12);

            $returnUrl = add_query_arg('faerie_return', '1', wp_get_referer() ?: home_url('/'));
            $payload   = [
                'sku'        => $sku,
                'user_id'    => $userId,
                'return_url' => $returnUrl,
            ];
            if ($mobile !== '')   $payload['mobile'] = $mobile;
            if ($national !== '') $payload['national_code'] = $national;
            if ($discount !== '') $payload['discount_code'] = $discount;

            $res = self::api_request('POST', '/api/v1/iap/init', $payload);
            if (!empty($res['success']) && !empty($res['data']['payment_url'])) {
                wp_redirect(esc_url_raw($res['data']['payment_url']));
                exit;
            }
            $err = (string)($res['message'] ?? 'خطا در اتصال به درگاه فائری');
            wp_redirect(add_query_arg('faerie_err', rawurlencode($err), wp_get_referer() ?: home_url('/')));
            exit;
        }
    }

    public static function render_shortcode($atts): string
    {
        $a = shortcode_atts([
            'sku'          => '',
            'label'        => 'پرداخت با فائری',
            'ask_identity' => 'yes',
        ], $atts, 'faerie_pay');

        ob_start();
        if (!empty($_GET['faerie_return']) && !empty($_GET['purchase_id'])) {
            $pid = sanitize_text_field(wp_unslash($_GET['purchase_id']));
            $st  = self::api_request('GET', '/api/v1/iap/status/' . rawurlencode($pid));
            $tx  = $st['data'] ?? [];
            $ok  = in_array($tx['status'] ?? '', ['paid', 'consumed'], true);
            if ($ok) {
                echo '<div dir="rtl" style="padding:14px 18px;border-radius:12px;background:#ecfdf5;border:1px solid #10b981;color:#065f46;margin:12px 0;">';
                echo '<strong>پرداخت شما با موفقیت تأیید شد!</strong><br>';
                echo 'شناسه خرید: <code>' . esc_html($pid) . '</code> | شماره فاکتور رسمی: <code>' . esc_html((string)($tx['invoice_number'] ?? '—')) . '</code>';
                echo '</div>';
            } else {
                echo '<div dir="rtl" style="padding:14px 18px;border-radius:12px;background:#fef2f2;border:1px solid #ef4444;color:#991b1b;margin:12px 0;">';
                echo 'پرداخت تکمیل نشد (وضعیت: ' . esc_html((string)($tx['status'] ?? 'نامشخص')) . ').';
                echo '</div>';
            }
        }
        if (!empty($_GET['faerie_err'])) {
            echo '<div dir="rtl" style="padding:12px 16px;border-radius:10px;background:#fff7ed;border:1px solid #f97316;color:#9a3412;margin:10px 0;">'
                . esc_html(rawurldecode(sanitize_text_field(wp_unslash($_GET['faerie_err'])))) . '</div>';
        }
        ?>
        <form method="post" dir="rtl" style="display:inline-flex;flex-direction:column;gap:8px;max-width:340px;margin:10px 0;">
            <?php wp_nonce_field('faerie_pay_start'); ?>
            <input type="hidden" name="faerie_pay_action" value="start">
            <input type="hidden" name="faerie_sku" value="<?php echo esc_attr($a['sku']); ?>">
            <?php if ($a['ask_identity'] === 'yes'): ?>
                <input type="tel" name="faerie_mobile" placeholder="شماره موبایل (۰۹۱۲...)" dir="ltr" style="padding:9px 12px;border:1px solid #cbd5e1;border-radius:10px;">
                <input type="text" name="faerie_national" maxlength="10" placeholder="کد ملی ۱۰ رقمی" dir="ltr" style="padding:9px 12px;border:1px solid #cbd5e1;border-radius:10px;">
            <?php endif; ?>
            <button type="submit" style="background:linear-gradient(135deg,#5B5CE6,#7C4DFF);color:#fff;border:none;border-radius:12px;padding:12px 20px;font-weight:700;cursor:pointer;">
                <?php echo esc_html($a['label']); ?>
            </button>
        </form>
        <?php
        return (string)ob_get_clean();
    }

    public static function init_woocommerce_gateway(): void
    {
        if (!class_exists('WC_Payment_Gateway')) {
            return;
        }
        require_once __DIR__ . '/includes/class-wc-gateway-faerie-payline.php';

        add_filter('woocommerce_payment_gateways', static function (array $methods): array {
            $methods[] = 'WC_Gateway_Faerie_Payline';
            return $methods;
        });
    }
}

Faerie_Payline_WP_Plugin::boot();
