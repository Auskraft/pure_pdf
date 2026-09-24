package com.auskraft.purepdf.ui.support

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.auskraft.purepdf.R

private val Gold = Color(0xFFC7933E)
private val GoldBorder = Color(0xFFE7BD76)
private val GoldInk = Color(0xFF26190B)

/** Small, fixed-size header action. Only its ring animates; the heart stays still. */
@Composable
fun SupportHeartButton(onClick: () -> Unit) {
    val context = LocalContext.current
    val animationsEnabled = Settings.Global.getFloat(
        context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) > 0f
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (animationsEnabled) SupportPulseRing()
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFFF1CA), Color(0xFFD9AD61))))
                .border(1.dp, GoldBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Поддержка разработчика", tint = GoldInk, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun SupportPulseRing() {
    val transition = rememberInfiniteTransition(label = "support pulse")
    val ringScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5700
                0.85f at 0
                1.16f at 1000
                1.27f at 2000
                0.85f at 2001
                0.85f at 5700
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring scale",
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5700
                0f at 0
                0.65f at 150
                0f at 2000
                0f at 5700
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring alpha",
    )
    Box(
        Modifier.size(34.dp).scale(ringScale)
            .border(1.5.dp, Gold.copy(alpha = ringAlpha), CircleShape),
    )
}

@Composable
fun SupportScreen(onBack: () -> Unit, onPayment: () -> Unit, onTerms: () -> Unit, onAbout: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val qr = remember { ImageBitmap.imageResource(context.resources, R.drawable.payment_qr) }
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.red < 0.3f
    val page = if (dark) colors.surface else Color(0xFFFFF7F9)
    val warm = if (dark) colors.surfaceContainer else Color(0xFFFFF9EE)
    val qrCard = if (dark) colors.surfaceContainerLow else Color.White

    Column(Modifier.fillMaxSize().background(page)) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
            }
            Text("Поддержка разработчика", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(warm)
                    .border(1.dp, GoldBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeartMark()
                Spacer(Modifier.height(18.dp))
                Text("Спасибо, что пользуетесь", color = colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pure PDF остаётся бесплатным для всех. Если хотите помочь проекту развиваться, поддержите автора",
                    color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                PaymentButton(onClick = onPayment)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(qrCard)
                    .border(1.dp, GoldBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("QR для другого устройства", color = colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = maxWidth.coerceAtMost(280.dp)
                    Image(
                        bitmap = qr,
                        contentDescription = "QR для страницы оплаты",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(side).clip(RoundedCornerShape(12.dp))
                            .background(Color.White).border(1.dp, GoldBorder, RoundedCornerShape(12.dp)).padding(10.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Откройте страницу оплаты камерой", color = colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onTerms) { Text("Условия поддержки", color = colors.primary) }
                TextButton(onClick = onAbout) { Text("Об авторе", color = colors.primary) }
            }
        }
    }
}

@Composable
private fun HeartMark() {
    Box(
        Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFFFF3D9)).border(1.dp, GoldBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.FavoriteBorder, null, tint = Gold, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun PaymentButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFEDB8), Color(0xFFD9A851))))
            .border(1.dp, GoldBorder, RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Поддержать по карте", color = GoldInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(8.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = GoldInk, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun SupportPrompt(onDismiss: () -> Unit, onPayment: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(colors.surface).border(1.dp, GoldBorder, RoundedCornerShape(22.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.Center)) { HeartMark() }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Нравится Pure PDF?", color = colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Если приложение вам полезно, поддержите его развитие. Это по желанию — все функции останутся бесплатными",
                color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PaymentButton(onClick = onPayment)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) { Text("Не сейчас") }
        }
    }
}
