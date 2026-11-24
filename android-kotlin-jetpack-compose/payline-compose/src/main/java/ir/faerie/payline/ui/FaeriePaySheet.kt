package ir.faerie.payline.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.faerie.payline.FaeriePaylineClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * کامپوننت آمادهٔ دکمهٔ «پرداخت با فائری» (مشابه BazaarPay) برای Jetpack Compose.
 *
 * با قرار دادن این کامپوننت در هر صفحه‌ای از برنامهٔ خود، فرایند کامل:
 * انتخاب/تأیید آیتم، کد تخفیف، احراز کد ملی (در صورت نیاز)، هدایت به برنامه/درگاه فائری،
 * بررسی خودکار وضعیت هنگام بازگشت کاربر به برنامه، مصرف آیتم (consume) و نمایش فاکتور رسمی
 * به‌صورت یکپارچه انجام می‌شود.
 */
@Composable
fun FaeriePayButton(
    client: FaeriePaylineClient,
    sku: String,
    userId: String,
    modifier: Modifier = Modifier,
    label: String = "پرداخت با فائری",
    autoConsume: Boolean = true,
    returnSchemeUrl: String? = null,
    onPaid: (FaeriePaylineClient.Purchase, FaeriePaylineClient.Invoice?) -> Unit,
    onError: (String) -> Unit = {}
) {
    var sheetOpen by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF5B5CE6), Color(0xFF7C4DFF))
                    )
                )
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                .clickable { sheetOpen = true }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("F", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (sheetOpen) {
        FaeriePaySheet(
            client = client,
            sku = sku,
            userId = userId,
            autoConsume = autoConsume,
            returnSchemeUrl = returnSchemeUrl,
            onDismiss = { sheetOpen = false },
            onSuccess = { purchase, invoice ->
                sheetOpen = false
                onPaid(purchase, invoice)
            },
            onFailure = onError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaeriePaySheet(
    client: FaeriePaylineClient,
    sku: String,
    userId: String,
    autoConsume: Boolean = true,
    returnSchemeUrl: String? = null,
    onDismiss: () -> Unit,
    onSuccess: (FaeriePaylineClient.Purchase, FaeriePaylineClient.Invoice?) -> Unit,
    onFailure: (String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var loading by remember { mutableStateOf(true) }
    var product by remember { mutableStateOf<FaeriePaylineClient.Product?>(null) }
    var activePurchase by remember { mutableStateOf<FaeriePaylineClient.Purchase?>(null) }
    var invoice by remember { mutableStateOf<FaeriePaylineClient.Invoice?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsNationalId by remember { mutableStateOf(false) }

    var mobile by remember { mutableStateOf("") }
    var nationalCode by remember { mutableStateOf("") }
    var discountCode by remember { mutableStateOf("") }
    var customAmount by remember { mutableStateOf("") }

    // بارگذاری مشخصات آیتم از سرور فائری
    LaunchedEffect(sku) {
        loading = true
        errorMsg = null
        try {
            val all = withContext(Dispatchers.IO) { client.getProducts() }
            product = all.firstOrNull { it.sku == sku }
            if (product == null) {
                errorMsg = "آیتم خرید ($sku) در پنل فائری فعال نیست."
            }
        } catch (e: FaeriePaylineClient.PaylineException) {
            errorMsg = e.message
        } finally {
            loading = false
        }
    }

    // به‌محض بازگشت کاربر از برنامه فائری یا مرورگر به برنامه، وضعیت پرداخت خودکار از سرور استعلام می‌شود
    fun verifyCurrentPurchase() {
        val pending = activePurchase ?: return
        scope.launch {
            loading = true
            errorMsg = null
            try {
                var status = withContext(Dispatchers.IO) { client.getStatus(pending.purchaseId) }
                if (status.isPaid) {
                    if (autoConsume && (product?.isConsumable != false) && status.status == "paid") {
                        status = withContext(Dispatchers.IO) { client.consume(status.purchaseId, userId) }
                    }
                    val inv = runCatching {
                        withContext(Dispatchers.IO) { client.getInvoice(status.purchaseId) }
                    }.getOrNull()
                    activePurchase = status
                    invoice = inv
                } else if (status.status != "pending") {
                    errorMsg = "وضعیت تراکنش: ${status.status}"
                }
            } catch (e: FaeriePaylineClient.PaylineException) {
                errorMsg = e.message
                onFailure(e.message ?: "خطای بررسی پرداخت")
            } finally {
                loading = false
            }
        }
    }

    DisposableEffect(lifecycleOwner, activePurchase?.purchaseId) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && activePurchase != null && invoice == null) {
                verifyCurrentPurchase()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "درگاه پرداخت امن فائری (Faerie Payline)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(12.dp))

                val paidPurchase = activePurchase?.takeIf { it.isPaid }
                if (paidPurchase != null) {
                    // نمایش رسید و فاکتور رسمی فائری
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("پرداخت با موفقیت انجام شد ✅", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                            Spacer(Modifier.height(6.dp))
                            Text("مبلغ پرداختی: ${paidPurchase.amountToman} تومان", fontSize = 13.sp)
                            if (paidPurchase.invoiceNumber.isNotBlank()) {
                                Text("شماره فاکتور رسمی: ${paidPurchase.invoiceNumber}", fontSize = 12.sp)
                            }
                            if (paidPurchase.refNumber.isNotBlank()) {
                                Text("کد پیگیری بانکی: ${paidPurchase.refNumber}", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onSuccess(paidPurchase, invoice) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تأیید و دریافت آیتم در برنامه")
                    }
                } else {
                    product?.let { p ->
                        Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (p.description.isNotBlank()) {
                            Text(p.description, fontSize = 12.5.sp, color = Color.Gray)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (p.customAmount) {
                            OutlinedTextField(
                                value = customAmount,
                                onValueChange = { customAmount = it.filter(Char::isDigit) },
                                label = { Text("مبلغ دلخواه (${p.minToman} تا ${p.maxToman} تومان)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("مبلغ قابل پرداخت: ${p.priceToman} تومان", fontWeight = FontWeight.Bold, color = Color(0xFF5B5CE6))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it.filter(Char::isDigit).take(11) },
                        label = { Text(if (needsNationalId) "شماره موبایل خریدار (الزامی)" else "شماره موبایل (اختیاری)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (needsNationalId) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nationalCode,
                            onValueChange = { nationalCode = it.filter(Char::isDigit).take(10) },
                            label = { Text("کد ملی ۱۰ رقمی خریدار") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discountCode,
                        onValueChange = { discountCode = it.trim() },
                        label = { Text("کد تخفیف (اختیاری)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!errorMsg.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, color = Color(0xFFC62828), fontSize = 12.5.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    if (activePurchase != null && !activePurchase!!.isPaid) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { openPaymentUrl(ctx, activePurchase!!.paymentUrl, activePurchase!!.purchaseId) },
                                modifier = Modifier.weight(1f),
                                enabled = !loading
                            ) { Text("ادامه پرداخت") }
                            OutlinedButton(
                                onClick = { verifyCurrentPurchase() },
                                modifier = Modifier.weight(1f),
                                enabled = !loading
                            ) { Text("بررسی وضعیت") }
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    errorMsg = null
                                    try {
                                        val started = withContext(Dispatchers.IO) {
                                            client.initPurchase(
                                                sku = sku,
                                                userId = userId,
                                                mobile = mobile.ifBlank { null },
                                                nationalCode = nationalCode.ifBlank { null },
                                                discountCode = discountCode.ifBlank { null },
                                                customAmountToman = customAmount.toIntOrNull(),
                                                returnUrl = returnSchemeUrl
                                            )
                                        }
                                        activePurchase = started
                                        openPaymentUrl(ctx, started.paymentUrl, started.purchaseId)
                                    } catch (e: FaeriePaylineClient.PaylineException) {
                                        if (e.requiresNationalId) {
                                            needsNationalId = true
                                        }
                                        errorMsg = e.message
                                        onFailure(e.message ?: "خطا در شروع پرداخت")
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading && product != null
                        ) {
                            Text(if (loading) "در حال اتصال…" else "ورود به درگاه فائری")
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** باز کردن برنامه فائری با دیپ‌لینک faerie://pay/{id} و در صورت نصب نبودن، باز کردن صفحهٔ وب /pay/{id} */
private fun openPaymentUrl(ctx: Context, paymentUrl: String, purchaseId: String) {
    val deepUri = Uri.parse("faerie://pay/${Uri.encode(purchaseId)}")
    val deepIntent = Intent(Intent.ACTION_VIEW, deepUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val openedApp = runCatching {
        ctx.startActivity(deepIntent)
        true
    }.getOrDefault(false)
    if (!openedApp && paymentUrl.isNotBlank()) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
