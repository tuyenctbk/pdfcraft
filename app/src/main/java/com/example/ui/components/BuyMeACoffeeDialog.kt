package com.example.ui.components

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
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
import com.example.ui.util.QrCodeGenerator

val CoffeeAmber = Color(0xFFD97706)
val CoffeeWarmContainer = Color(0xFF78350F)
val CoffeeGold = Color(0xFFF59E0B)

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
 * Buy Me a Coffee / Support Developer Interactive Card.
 * Designed for SettingsScreen and standalone containers.
 */
@Composable
fun BuyMeACoffeeCard(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onOpenQr: (() -> Unit)? = null,
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showThankYou by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    if (showQrDialog && onOpenQr == null) {
        BuyMeACoffeeQrDialog(
            onDismiss = { showQrDialog = false },
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
                                text = "paypal.me/tuyenphamvn",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = CoffeeAmber,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CoffeeAmber.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, CoffeeAmber.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "100% Free",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoffeeAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = stringResource(R.string.support_dev_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Preset Donation Options Row ($3, $5, $10)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        openDonationLink(context, 3) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$3 ☕",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = {
                        openDonationLink(context, 5) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$5 🍕",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = {
                        openDonationLink(context, 10) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$10 🚀",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Scan QR Code & Donate via PayPal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Scan QR Button
                OutlinedButton(
                    onClick = {
                        if (onOpenQr != null) {
                            onOpenQr()
                        } else {
                            showQrDialog = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, CoffeeAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CoffeeAmber
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("scan_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.support_qr_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Custom PayPal Button
                Button(
                    onClick = {
                        openDonationLink(context, null) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoffeeAmber,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp)
                        .testTag("paypal_donate_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolunteerActivism,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.support_paypal_custom),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Copy Link Button
            TextButton(
                onClick = {
                    val url = "https://paypal.me/tuyenphamvn"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PayPal Link", url))
                    onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                    showThankYou = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.support_copy_link),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showThankYou,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = CoffeeAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.support_thank_you),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CoffeeAmber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * QR Code Display Component.
 * Self-contained, responsive, and adaptive.
 */
@Composable
fun BuyMeACoffeeQrView(
    modifier: Modifier = Modifier,
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedAmount by remember { mutableStateOf<Int?>(null) }

    val currentUrl = remember(selectedAmount) {
        if (selectedAmount != null && selectedAmount!! > 0) {
            "https://paypal.me/tuyenphamvn/${selectedAmount}USD"
        } else {
            "https://paypal.me/tuyenphamvn"
        }
    }

    val qrBitmap = remember(currentUrl) {
        QrCodeGenerator.generateQrBitmap(
            content = currentUrl,
            sizePx = 512,
            darkColor = android.graphics.Color.BLACK,
            lightColor = android.graphics.Color.WHITE
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Amount Selection Chips with clean compact styling
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedAmount == null,
                onClick = { selectedAmount = null },
                label = { Text(stringResource(R.string.support_qr_amount_any), fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedAmount == 3,
                onClick = { selectedAmount = 3 },
                label = { Text(stringResource(R.string.support_qr_amount_3), fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedAmount == 5,
                onClick = { selectedAmount = 5 },
                label = { Text(stringResource(R.string.support_qr_amount_5), fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedAmount == 10,
                onClick = { selectedAmount = 10 },
                label = { Text(stringResource(R.string.support_qr_amount_10), fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // High Contrast QR Display Container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, CoffeeAmber.copy(alpha = 0.3f)),
            shadowElevation = 3.dp,
            modifier = Modifier.size(175.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.support_qr_title),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = CoffeeAmber)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = currentUrl.removePrefix("https://"),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = CoffeeAmber,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.support_qr_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Action: Open PayPal
        Button(
            onClick = {
                openDonationLink(context, selectedAmount)
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CoffeeAmber),
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
                text = stringResource(R.string.support_paypal_custom),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Actions: Share & Copy side by side with plenty of room
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Donation Link"))
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Share Link",
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Donation URL", currentUrl))
                    onShowMessage?.invoke(context.getString(R.string.support_link_copied, currentUrl))
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copy Link",
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Scan QR Code to "Buy Me a Coffee" Modal Dialog.
 * Used when launching directly from settings or shortcuts.
 */
@Composable
fun BuyMeACoffeeQrDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShowMessage: ((String) -> Unit)? = null
) {
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
                .testTag("buy_me_a_coffee_qr_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CoffeeAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = CoffeeAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.support_qr_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
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

                Spacer(modifier = Modifier.height(14.dp))

                BuyMeACoffeeQrView(onShowMessage = onShowMessage)
            }
        }
    }
}

/**
 * Comprehensive Buy Me a Coffee Dialog with Dual Modes:
 * Mode 0: Direct Options ($3, $5, $10, PayPal, Copy)
 * Mode 1: Scan QR Code (dynamic QR generator for Camera / PayPal App)
 * Seamlessly toggles within one dialog without nested stacking!
 */
@Composable
fun BuyMeACoffeeDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    onShowMessage: ((String) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
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
                                imageVector = if (selectedTab == 0) Icons.Default.LocalCafe else Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = CoffeeAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(if (selectedTab == 0) R.string.support_dev_title else R.string.support_qr_title),
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

                // Mode Selector Tabs (Options vs Scan QR)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = null,
                                tint = if (selectedTab == 0) CoffeeAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Options",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.support_qr_button),
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Smooth animated transition between Options and Scan QR
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "coffee_mode_switch"
                ) { tab ->
                    if (tab == 0) {
                        BuyMeACoffeeCard(
                            showHeader = false,
                            onOpenQr = { selectedTab = 1 },
                            onShowMessage = onShowMessage
                        )
                    } else {
                        BuyMeACoffeeQrView(
                            onShowMessage = onShowMessage
                        )
                    }
                }
            }
        }
    }
}
