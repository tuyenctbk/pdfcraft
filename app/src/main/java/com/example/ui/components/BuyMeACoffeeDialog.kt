package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Designed for SettingsScreen, HubScreen, and About screens.
 */
@Composable
fun BuyMeACoffeeCard(
    modifier: Modifier = Modifier,
    onShowMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showThankYou by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    if (showQrDialog) {
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
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CoffeeAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = null,
                            tint = CoffeeAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.support_dev_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "paypal.me/tuyenphamvn",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CoffeeAmber,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

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

            Text(
                text = stringResource(R.string.support_dev_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$3 ☕",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        openDonationLink(context, 5) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$5 🍕",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        openDonationLink(context, 10) { url ->
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, url))
                        }
                        showThankYou = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$10 🚀",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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
                    onClick = { showQrDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoffeeAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CoffeeAmber
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
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
                        fontSize = 12.sp
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoffeeAmber,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp)
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
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
 * Scan QR Code to "Buy Me a Coffee" Modal Dialog.
 * Generates dynamic high-contrast QR code for instant camera or PayPal scan.
 */
@Composable
fun BuyMeACoffeeQrDialog(
    onDismiss: () -> Unit,
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

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedAmount == null,
                        onClick = { selectedAmount = null },
                        label = { Text(stringResource(R.string.support_qr_amount_any), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedAmount == 3,
                        onClick = { selectedAmount = 3 },
                        label = { Text(stringResource(R.string.support_qr_amount_3), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedAmount == 5,
                        onClick = { selectedAmount = 5 },
                        label = { Text(stringResource(R.string.support_qr_amount_5), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedAmount == 10,
                        onClick = { selectedAmount = 10 },
                        label = { Text(stringResource(R.string.support_qr_amount_10), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // High Contrast QR Display Container
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, CoffeeAmber.copy(alpha = 0.3f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(230.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.support_qr_subtitle),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Share QR, Copy Link, Open PayPal
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Donation URL", currentUrl))
                            onShowMessage?.invoke(context.getString(R.string.support_link_copied, currentUrl))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy",
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            openDonationLink(context, selectedAmount)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeeAmber),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(
                            text = "PayPal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Buy Me a Coffee Dialog for easy modal access from Hub or Tool screens.
 */
@Composable
fun BuyMeACoffeeDialog(
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
                .testTag("buy_me_a_coffee_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
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
                                imageVector = Icons.Default.LocalCafe,
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

                BuyMeACoffeeCard(onShowMessage = onShowMessage)
            }
        }
    }
}
