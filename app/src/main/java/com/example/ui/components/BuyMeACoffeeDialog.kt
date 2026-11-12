package com.example.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.billing.BillingManager
import com.example.ui.billing.BillingStatus
import com.example.ui.billing.TipProduct
import com.example.ui.util.QrCodeGenerator
import com.example.ui.util.SatisfactionManager

val CoffeeAmber = Color(0xFFD97706)
val CoffeeWarmContainer = Color(0xFF78350F)
val CoffeeGold = Color(0xFFF59E0B)

const val KOFI_URL = "https://ko-fi.com/tuyenphamvn"

/**
 * Utility function to launch donation link or copy to clipboard on failure.
 */
fun openDonationLink(context: Context, amountUsd: Int? = null, onCopyFallback: ((String) -> Unit)? = null) {
    val url = if (amountUsd != null && amountUsd > 0) {
        "https://paypal.me/tuyenphamvn/${amountUsd}USD"
    } else {
        "https://paypal.me/tuyenphamvn"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("PDFCraft Donation", url))
        onCopyFallback?.invoke(url)
    }
}

/**
 * Buy Me a Coffee / Support Developer Interactive Card for SettingsScreen.
 */
@Composable
fun BuyMeACoffeeCard(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onOpenDialog: (() -> Unit)? = null,
    onShowMessage: ((String) -> Unit)? = null
) {
    var showFullDialog by remember { mutableStateOf(false) }
    var dialogInitialTab by remember { mutableIntStateOf(0) }

    if (showFullDialog && onOpenDialog == null) {
        BuyMeACoffeeDialog(
            onDismiss = { showFullDialog = false },
            initialTab = dialogInitialTab,
            onShowMessage = onShowMessage
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("buy_me_a_coffee_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showHeader) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CoffeeAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = null,
                                tint = CoffeeAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.support_dev_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "100% Offline • Private • Indie Dev",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Surface(
                        color = CoffeeAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "❤️ Optional",
                            color = CoffeeAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = stringResource(R.string.support_dev_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action row: In-App Purchase / QR Scanner Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        dialogInitialTab = 0
                        if (onOpenDialog != null) onOpenDialog() else showFullDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoffeeAmber,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.support_tab_google_play),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        dialogInitialTab = 1
                        if (onOpenDialog != null) onOpenDialog() else showFullDialog = true
                    },
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CoffeeAmber
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.support_tab_paypal_qr),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Unwraps ContextWrapper instances (such as Dialog or ContextThemeWrapper contexts)
 * to retrieve the underlying Android Activity.
 */
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * Tab 0: Google Play In-App Purchase 1-Tap Tips.
 */
@Composable
fun GooglePlayTipView(
    activity: Activity? = null,
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val billingRepository = remember { com.example.ui.billing.BillingRepository.getInstance(context) }
    val resolvedActivity = activity ?: remember(context) { context.findActivity() } ?: billingRepository.resolveActiveActivity()
    val availableTips by billingRepository.availableTips.collectAsState()
    val billingStatus by billingRepository.billingStatus.collectAsState()
    val satisfactionManager = remember { SatisfactionManager.getInstance(context) }
    val isSupporter by satisfactionManager.isSupporter.collectAsState()
    var lastPurchasedTip by remember { mutableStateOf<TipProduct?>(null) }

    val confirmingTip = (billingStatus as? BillingStatus.ConfirmingCheckout)?.tip
    val successStatus = billingStatus as? BillingStatus.Success

    LaunchedEffect(successStatus) {
        if (successStatus != null) {
            val matched = availableTips.firstOrNull { it.id == successStatus.productId }
            if (matched != null) {
                lastPurchasedTip = matched
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Supporter Recognition Banner with Tier Badge
        if (isSupporter) {
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.support_status_active),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF34D399)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "UNLOCKED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (lastPurchasedTip != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${lastPurchasedTip!!.emoji} ${lastPurchasedTip!!.title} (${lastPurchasedTip!!.defaultPriceFormatted})" +
                                    (successStatus?.orderId?.let { " • #$it" } ?: ""),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF6EE7B7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.support_thank_you_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Direct, Clear Value Proposition Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = CoffeeAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.support_iap_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "100% optional one-time tip via Google Play Balance, Google Pay, or Card. No recurring charges or subscriptions.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Google Play 1-Tap Checkout Sheet when a tier is pending confirmation
        AnimatedVisibility(visible = confirmingTip != null) {
            confirmingTip?.let { pendingTip ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.5.dp, CoffeeAmber),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("google_play_checkout_sheet")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = null,
                                    tint = CoffeeAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Confirm 1-Tap Support",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoffeeAmber
                                )
                            }
                            Surface(
                                color = CoffeeAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "One-Time",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoffeeAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = pendingTip.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pendingTip.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pendingTip.description,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = pendingTip.defaultPriceFormatted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { billingRepository.resetStatus() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    billingRepository.confirmCheckoutPurchase(pendingTip) { _, msg ->
                                        lastPurchasedTip = pendingTip
                                        onShowMessage?.invoke(msg)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(42.dp)
                                    .testTag("google_play_confirm_buy_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Send ${pendingTip.defaultPriceFormatted}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Product Tiers: Clean, scannable cards with emoji, title, clear one-time badge & price button
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            availableTips.forEach { tip ->
                val isPurchasedTier = lastPurchasedTip?.id == tip.id
                val isConfirmingThisTier = confirmingTip?.id == tip.id
                Surface(
                    onClick = {
                        billingRepository.launchTipPurchase(resolvedActivity, tip) { _, msg ->
                            lastPurchasedTip = tip
                            onShowMessage?.invoke(msg)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isConfirmingThisTier -> CoffeeAmber.copy(alpha = 0.22f)
                        isPurchasedTier -> Color(0xFF10B981).copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        width = if (isConfirmingThisTier || isPurchasedTier) 1.5.dp else 1.dp,
                        color = when {
                            isConfirmingThisTier -> CoffeeAmber
                            isPurchasedTier -> Color(0xFF34D399)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPurchasedTier) Color(0xFF10B981).copy(alpha = 0.2f)
                                        else CoffeeAmber.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tip.emoji,
                                    fontSize = 22.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tip.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isPurchasedTier) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFF10B981),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tip.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                billingRepository.launchTipPurchase(resolvedActivity, tip) { _, msg ->
                                    lastPurchasedTip = tip
                                    onShowMessage?.invoke(msg)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPurchasedTier) Color(0xFF10B981) else CoffeeAmber,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isPurchasedTier) "✓ ${tip.defaultPriceFormatted}" else tip.defaultPriceFormatted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Trust badge: 100% offline & safe Google Play processing guarantee
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Processed securely by Google Play. Instant offline unlock.",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Tab 1: PayPal & Dynamic QR Code Scanner.
 */
@Composable
fun BuyMeACoffeeQrView(
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedAmount by remember { mutableStateOf<Int?>(null) }

    val donationUrl = remember(selectedAmount) {
        if (selectedAmount != null && selectedAmount!! > 0) {
            "https://paypal.me/tuyenphamvn/${selectedAmount}USD"
        } else {
            "https://paypal.me/tuyenphamvn"
        }
    }

    val qrBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = donationUrl) {
        value = QrCodeGenerator.generateQrBitmap(
            content = donationUrl,
            sizePx = 512
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.support_qr_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // High-Contrast QR Code Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, CoffeeAmber.copy(alpha = 0.35f)),
            shadowElevation = 3.dp,
            modifier = Modifier.size(170.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(10.dp)
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR Code to Donate via PayPal",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = CoffeeAmber,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Amount Selection Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            FilterChip(
                selected = selectedAmount == null,
                onClick = { selectedAmount = null },
                label = { Text(stringResource(R.string.support_qr_amount_any), fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoffeeAmber.copy(alpha = 0.2f),
                    selectedLabelColor = CoffeeAmber
                )
            )
            FilterChip(
                selected = selectedAmount == 3,
                onClick = { selectedAmount = 3 },
                label = { Text(stringResource(R.string.support_qr_amount_3), fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoffeeAmber.copy(alpha = 0.2f),
                    selectedLabelColor = CoffeeAmber
                )
            )
            FilterChip(
                selected = selectedAmount == 5,
                onClick = { selectedAmount = 5 },
                label = { Text(stringResource(R.string.support_qr_amount_5), fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoffeeAmber.copy(alpha = 0.2f),
                    selectedLabelColor = CoffeeAmber
                )
            )
            FilterChip(
                selected = selectedAmount == 10,
                onClick = { selectedAmount = 10 },
                label = { Text(stringResource(R.string.support_qr_amount_10), fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoffeeAmber.copy(alpha = 0.2f),
                    selectedLabelColor = CoffeeAmber
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = donationUrl.replace("https://", ""),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = CoffeeAmber,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Primary Action: Launch PayPal
        Button(
            onClick = {
                openDonationLink(context, selectedAmount) { fallbackUrl ->
                    onShowMessage?.invoke(context.getString(R.string.support_link_copied, fallbackUrl))
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = CoffeeAmber,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.LocalCafe,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.support_paypal_custom),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Secondary Actions: Share & Copy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Support PDFCraft indie development: $donationUrl")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.support_qr_share))
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(shareIntent)
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Share Link",
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PDFCraft PayPal Link", donationUrl))
                    onShowMessage?.invoke(context.getString(R.string.support_link_copied, donationUrl))
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.support_copy_link),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
/**
 * Tab 2: Web (Ko-fi) Tip Jar View.
 */
@Composable
fun WebTipView(
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.5.dp, CoffeeAmber.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CoffeeAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = CoffeeAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Ko-fi Tip Jar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Text(
                    text = "ko-fi.com/tuyenphamvn",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CoffeeAmber,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Support independent offline development with a quick one-time tip via Ko-fi.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Ko-fi URL", KOFI_URL))
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, KOFI_URL))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoffeeAmber,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolunteerActivism,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.support_kofi_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Multi-Method Buy Me a Coffee Dialog:
 * Tab 0: [ 🛍️ Google Play ] (1-Tap Google Pay In-App Purchases: $1.99, $4.99, $9.99, $19.99)
 * Tab 1: [ 📷 PayPal & QR ] (PayPal presets & live QR code generator)
 * Tab 2: [ 🌐 Web / Ko-fi ] (Ko-fi tip jar with direct support button)
 */
@Composable
fun BuyMeACoffeeDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    onShowMessage: ((String) -> Unit)? = null
) {
    val outerContext = LocalContext.current
    val hostActivity = remember(outerContext) { outerContext.findActivity() }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var dialogNotice by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .testTag("buy_me_a_coffee_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header with Title & Dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CoffeeAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (selectedTab) {
                                    0 -> Icons.Default.ShoppingBag
                                    1 -> Icons.Default.QrCode2
                                    else -> Icons.Default.VolunteerActivism
                                },
                                contentDescription = null,
                                tint = CoffeeAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.support_dev_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Tabs (Google Play | PayPal & QR | Web / Ko-fi)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Google Play
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (selectedTab == 0) BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.5f)) else null,
                        shadowElevation = if (selectedTab == 0) 2.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = if (selectedTab == 0) CoffeeAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.support_tab_google_play),
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 1: PayPal & QR
                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (selectedTab == 1) BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.5f)) else null,
                        shadowElevation = if (selectedTab == 1) 2.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = if (selectedTab == 1) CoffeeAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.support_tab_paypal_qr),
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 2: Web / Ko-fi
                    Surface(
                        onClick = { selectedTab = 2 },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 2) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (selectedTab == 2) BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.5f)) else null,
                        shadowElevation = if (selectedTab == 2) 2.dp else 0.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolunteerActivism,
                                contentDescription = null,
                                tint = if (selectedTab == 2) CoffeeAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.support_tab_web_crypto),
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !dialogNotice.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = dialogNotice ?: "",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Smooth animated transition between the 3 modes
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "coffee_mode_switch"
                ) { tab ->
                    when (tab) {
                        0 -> GooglePlayTipView(
                            activity = hostActivity,
                            onShowMessage = { msg -> dialogNotice = msg }
                        )
                        1 -> BuyMeACoffeeQrView(
                            onShowMessage = { msg -> dialogNotice = msg }
                        )
                        else -> WebTipView(
                            onShowMessage = { msg -> dialogNotice = msg }
                        )
                    }
                }
            }
        }
    }
}
