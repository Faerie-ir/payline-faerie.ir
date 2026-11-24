library faerie_payline;

import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

/// مدل آیتم خرید تعریف‌شده در پنل فائری
class FaerieProduct {
  final String sku;
  final String title;
  final String description;
  final String type;
  final int priceToman;
  final bool customAmount;
  final int minToman;
  final int maxToman;

  const FaerieProduct({
    required this.sku,
    required this.title,
    required this.description,
    required this.type,
    required this.priceToman,
    required this.customAmount,
    required this.minToman,
    required this.maxToman,
  });

  bool get isConsumable => type == 'consumable';

  factory FaerieProduct.fromJson(Map<String, dynamic> j) => FaerieProduct(
        sku: (j['sku'] ?? '').toString(),
        title: (j['title'] ?? '').toString(),
        description: (j['description'] ?? '').toString(),
        type: (j['type'] ?? 'consumable').toString(),
        priceToman: (j['price_toman'] as num?)?.toInt() ?? 0,
        customAmount: j['custom_amount'] == true,
        minToman: (j['min_toman'] as num?)?.toInt() ?? 0,
        maxToman: (j['max_toman'] as num?)?.toInt() ?? 0,
      );
}

/// مدل تراکنش خرید فائری
class FaeriePurchase {
  final String purchaseId;
  final String sku;
  final int amountToman;
  final int grossToman;
  final int discountToman;
  final String status;
  final String refNumber;
  final String paymentUrl;
  final String invoiceNumber;

  const FaeriePurchase({
    required this.purchaseId,
    required this.sku,
    required this.amountToman,
    required this.grossToman,
    required this.discountToman,
    required this.status,
    required this.refNumber,
    required this.paymentUrl,
    required this.invoiceNumber,
  });

  bool get isPaid => status == 'paid' || status == 'consumed';

  factory FaeriePurchase.fromJson(Map<String, dynamic> j, [String fallbackUrl = '']) => FaeriePurchase(
        purchaseId: (j['purchase_id'] ?? '').toString(),
        sku: (j['sku'] ?? '').toString(),
        amountToman: (j['amount_toman'] as num?)?.toInt() ?? 0,
        grossToman: (j['gross_toman'] as num?)?.toInt() ?? 0,
        discountToman: (j['discount_toman'] as num?)?.toInt() ?? 0,
        status: (j['status'] ?? 'pending').toString(),
        refNumber: (j['ref_number'] ?? '').toString(),
        paymentUrl: (j['payment_url'] ?? '').toString().isNotEmpty
            ? j['payment_url'].toString()
            : fallbackUrl,
        invoiceNumber: (j['invoice_number'] ?? '').toString(),
      );
}

class FaeriePaylineException implements Exception {
  final String message;
  final int statusCode;
  final bool requiresNationalId;

  const FaeriePaylineException(this.message, {this.statusCode = 0, this.requiresNationalId = false});

  @override
  String toString() => message;
}

/// کلاینت رسمی اتصال Flutter به درگاه Faerie Payline
class FaeriePaylineClient {
  final String baseUrl;
  final String paylineToken;

  FaeriePaylineClient({
    this.baseUrl = 'https://faerie.ir',
    required this.paylineToken,
  });

  String get _cleanBase => baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl;

  Map<String, String> get _headers => {
        'Accept': 'application/json',
        'Content-Type': 'application/json; charset=utf-8',
        'X-Payline': paylineToken,
      };

  Future<List<FaerieProduct>> getProducts() async {
    final res = await http.get(Uri.parse('$_cleanBase/api/v1/iap/products'), headers: _headers);
    final root = _parseResponse(res);
    final list = (root['data']?['products'] as List?) ?? const [];
    return list.map((e) => FaerieProduct.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<FaeriePurchase> initPurchase({
    required String sku,
    required String userId,
    String? mobile,
    String? nationalCode,
    String? discountCode,
    int? customAmountToman,
    String? returnUrl,
  }) async {
    final payload = <String, dynamic>{'sku': sku, 'user_id': userId};
    if (mobile != null && mobile.isNotEmpty) payload['mobile'] = mobile;
    if (nationalCode != null && nationalCode.isNotEmpty) payload['national_code'] = nationalCode;
    if (discountCode != null && discountCode.isNotEmpty) payload['discount_code'] = discountCode;
    if (customAmountToman != null && customAmountToman > 0) payload['amount'] = customAmountToman;
    if (returnUrl != null && returnUrl.isNotEmpty) payload['return_url'] = returnUrl;

    final res = await http.post(
      Uri.parse('$_cleanBase/api/v1/iap/init'),
      headers: _headers,
      body: jsonEncode(payload),
    );
    final root = _parseResponse(res);
    final data = Map<String, dynamic>.from(root['data'] as Map);
    final pMap = Map<String, dynamic>.from((data['purchase'] ?? data) as Map);
    return FaeriePurchase.fromJson(pMap, (data['payment_url'] ?? '').toString());
  }

  Future<FaeriePurchase> getStatus(String purchaseId) async {
    final res = await http.get(
      Uri.parse('$_cleanBase/api/v1/iap/status/${Uri.encodeComponent(purchaseId)}'),
      headers: _headers,
    );
    final root = _parseResponse(res);
    return FaeriePurchase.fromJson(Map<String, dynamic>.from(root['data'] as Map));
  }

  Future<FaeriePurchase> consume(String purchaseId, String userId) async {
    final res = await http.post(
      Uri.parse('$_cleanBase/api/v1/iap/consume'),
      headers: _headers,
      body: jsonEncode({'purchase_id': purchaseId, 'user_id': userId}),
    );
    final root = _parseResponse(res);
    return FaeriePurchase.fromJson(Map<String, dynamic>.from(root['data'] as Map));
  }

  Future<Map<String, dynamic>> getInvoice(String purchaseId) async {
    final res = await http.get(
      Uri.parse('$_cleanBase/api/v1/iap/invoice/${Uri.encodeComponent(purchaseId)}'),
      headers: _headers,
    );
    final root = _parseResponse(res);
    return Map<String, dynamic>.from(root['data'] as Map);
  }

  Future<void> openGateway(FaeriePurchase purchase) async {
    final deep = Uri.parse('faerie://pay/${Uri.encodeComponent(purchase.purchaseId)}');
    if (await canLaunchUrl(deep)) {
      await launchUrl(deep, mode: LaunchMode.externalApplication);
      return;
    }
    if (purchase.paymentUrl.isNotEmpty) {
      await launchUrl(Uri.parse(purchase.paymentUrl), mode: LaunchMode.externalApplication);
    }
  }

  Map<String, dynamic> _parseResponse(http.Response res) {
    final decoded = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    if (res.statusCode < 200 || res.statusCode >= 300 || decoded['success'] != true) {
      throw FaeriePaylineException(
        (decoded['message'] ?? 'خطای ارتباط با درگاه فائری').toString(),
        statusCode: res.statusCode,
        requiresNationalId: decoded['requires_national_id'] == true,
      );
    }
    return decoded;
  }
}

/// ویجت آمادهٔ دکمهٔ «پرداخت با فائری» برای Flutter با بررسی خودکار هنگام بازگشت به برنامه
class FaeriePayButton extends StatefulWidget {
  final FaeriePaylineClient client;
  final String sku;
  final String userId;
  final String label;
  final bool autoConsume;
  final void Function(FaeriePurchase purchase) onPaid;
  final void Function(String message)? onError;

  const FaeriePayButton({
    super.key,
    required this.client,
    required this.sku,
    required this.userId,
    this.label = 'پرداخت با فائری',
    this.autoConsume = true,
    required this.onPaid,
    this.onError,
  });

  @override
  State<FaeriePayButton> createState() => _FaeriePayButtonState();
}

class _FaeriePayButtonState extends State<FaeriePayButton> with WidgetsBindingObserver {
  String? _pendingPurchaseId;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _pendingPurchaseId != null) {
      final pid = _pendingPurchaseId!;
      _pendingPurchaseId = null;
      _verify(pid);
    }
  }

  Future<void> _start() async {
    setState(() => _busy = true);
    try {
      final tx = await widget.client.initPurchase(sku: widget.sku, userId: widget.userId);
      _pendingPurchaseId = tx.purchaseId;
      await widget.client.openGateway(tx);
    } on FaeriePaylineException catch (e) {
      widget.onError?.call(e.message);
    } catch (e) {
      widget.onError?.call(e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify(String pid) async {
    setState(() => _busy = true);
    try {
      var st = await widget.client.getStatus(pid);
      if (st.isPaid) {
        if (widget.autoConsume && st.status == 'paid') {
          st = await widget.client.consume(pid, widget.userId);
        }
        widget.onPaid(st);
      } else {
        widget.onError?.call('پرداخت تکمیل نشد (${st.status}).');
      }
    } catch (e) {
      widget.onError?.call(e.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: _busy ? null : _start,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 13),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            gradient: const LinearGradient(colors: [Color(0xFF5B5CE6), Color(0xFF7C4DFF)]),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.verified_user_rounded, color: Colors.white, size: 18),
              const SizedBox(width: 8),
              Text(
                _busy ? 'در حال اتصال به فائری…' : widget.label,
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
