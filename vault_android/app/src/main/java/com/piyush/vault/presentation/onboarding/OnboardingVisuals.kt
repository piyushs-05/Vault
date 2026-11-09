package com.piyush.vault.presentation.onboarding

import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piyush.vault.presentation.ui.components.VaultMascot
import kotlin.math.roundToInt

private val IllustrationBackground = Color(0xFFF0F5F1)
private val VaultGreen = Color(0xFF176B58)
private val CoinGold = Color(0xFFE7C879)
private val CoinEdge = Color(0xFFB38D47)

@Composable
fun CoinVaultScene(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "coin-vault")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label = "coin-drop-progress",
    )
    val coinOffset = when {
        progress < .18f -> -8f
        progress < .51f -> lerp(-8f, 25f, (progress - .18f) / .33f)
        progress < .58f -> lerp(25f, 34f, (progress - .51f) / .07f)
        progress < .64f -> lerp(34f, 32f, (progress - .58f) / .06f)
        else -> 32f
    }
    val coinAlpha = if (progress < .14f) 0f else 1f

    Canvas(
        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(22.dp))
            .semantics { contentDescription = "A coin drops onto a growing pile inside a see-through vault"; role = Role.Image },
    ) {
        val d = density
        drawRoundRect(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF1F6F2), Color(0xFFE8F0EB))), cornerRadius = CornerRadius(22f * d))
        val shellWidth = 142f * d
        val shellHeight = 112f * d
        val shellLeft = (size.width - shellWidth) / 2f
        val shellTop = (size.height - shellHeight) / 2f + 15f * d
        val shellSize = Size(shellWidth, shellHeight)
        val shellRadius = CornerRadius(25f * d)

        drawRoundRect(Color(0xFFDFECE3).copy(alpha = .48f), Offset(shellLeft, shellTop), shellSize, shellRadius)
        drawRoundRect(Color.White.copy(alpha = .25f), Offset(shellLeft + 17f * d, shellTop + 28f * d), Size(shellWidth - 34f * d, shellHeight - 40f * d), CornerRadius(14f * d))

        fun drawPileCoin(leftDp: Float, bottomDp: Float) {
            val coinLeft = shellLeft + leftDp * d
            val coinTop = shellTop + shellHeight - 12f * d - bottomDp * d - 15f * d
            val coinSize = Size(20f * d, 15f * d)
            drawOval(CoinGold, Offset(coinLeft, coinTop), coinSize)
            drawOval(CoinEdge, Offset(coinLeft, coinTop), coinSize, style = Stroke(1f * d))
            drawOval(Color.White.copy(alpha = .45f), Offset(coinLeft + 5f * d, coinTop + 4f * d), Size(10f * d, 6f * d), style = Stroke(.8f * d))
        }
        listOf(4f, 24f, 44f, 64f, 84f).forEach { drawPileCoin(it, 0f) }
        listOf(14f, 34f, 54f, 74f).forEach { drawPileCoin(it, 11f) }
        listOf(24f, 44f, 64f).forEach { drawPileCoin(it, 22f) }
        listOf(34f, 54f).forEach { drawPileCoin(it, 33f) }

        // A faint glass sheen keeps the stack visible while still reading as behind glass.
        drawRoundRect(Color.White.copy(alpha = .09f), Offset(shellLeft + 17f * d, shellTop + 28f * d), Size(shellWidth - 34f * d, shellHeight - 40f * d), CornerRadius(14f * d))
        drawRoundRect(Color(0xFF9DB9AA), Offset(shellLeft, shellTop), shellSize, shellRadius, style = Stroke(2f * d))
        drawRoundRect(Color(0xFFD8E7DD), Offset(shellLeft + 13f * d, shellTop + 10f * d), Size(shellWidth - 26f * d, 14f * d), CornerRadius(10f * d))
        drawRoundRect(Color(0xFF648C78), Offset((size.width - 34f * d) / 2f, shellTop + 15f * d), Size(34f * d, 4f * d), CornerRadius(4f * d))

        if (coinAlpha > 0f) {
            val coinSize = 22f * d
            val coinLeft = (size.width - coinSize) / 2f
            val coinTop = shellTop - 2f * d + coinOffset * d
            drawCircle(CoinEdge.copy(alpha = coinAlpha), coinSize * .5f, Offset(coinLeft + coinSize * .5f, coinTop + coinSize * .5f + 2f * d))
            drawCircle(CoinGold.copy(alpha = coinAlpha), coinSize * .43f, Offset(coinLeft + coinSize * .5f, coinTop + coinSize * .5f))
            val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(114, 85, 38)
                textSize = 12f * d
                textAlign = NativePaint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                alpha = (coinAlpha * 255).roundToInt()
            }
            drawContext.canvas.nativeCanvas.drawText("₹", size.width / 2f, coinTop + coinSize * .68f, paint)
        }
    }
}

enum class SetupIllustrationKind { ACCOUNT, MESSAGES, REMINDER }

@Composable
fun SetupIllustration(kind: SetupIllustrationKind, caption: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(146.dp).clip(RoundedCornerShape(20.dp)).background(IllustrationBackground)
            .semantics { contentDescription = caption; role = Role.Image },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(Color(0xFFDCE8DF), 61f * density, Offset(cx, cy), style = Stroke(1f * density))
            drawCircle(Color(0xFFE1EBE4), 48f * density, Offset(cx, cy), style = Stroke(1f * density))
            val tile = 68f * density
            drawRoundRect(Color.White, Offset(cx - tile / 2, cy - tile / 2), Size(tile, tile), CornerRadius(22f * density))
            drawRoundRect(Color(0xFFD7E5DB), Offset(cx - tile / 2, cy - tile / 2), Size(tile, tile), CornerRadius(22f * density), style = Stroke(1f * density))
            val green = VaultGreen
            when (kind) {
                SetupIllustrationKind.ACCOUNT -> {
                    val x = cx - 17f * density; val y = cy - 12f * density
                    drawRoundRect(green, Offset(x, y), Size(34f * density, 25f * density), CornerRadius(5f * density), style = Stroke(2f * density))
                    drawLine(green, Offset(x, y + 7f * density), Offset(x + 34f * density, y + 7f * density), 2f * density)
                    drawRoundRect(green, Offset(x + 17f * density, y + 12f * density), Size(17f * density, 9f * density), CornerRadius(4f * density))
                    drawCircle(Color.White, 1.2f * density, Offset(x + 22f * density, y + 16.5f * density))
                }
                SetupIllustrationKind.MESSAGES -> {
                    val x = cx - 19f * density; val y = cy - 15f * density
                    drawRoundRect(green, Offset(x, y), Size(38f * density, 28f * density), CornerRadius(9f * density), style = Stroke(2f * density))
                    val tail = Path().apply { moveTo(x + 7f * density, y + 25f * density); lineTo(x + 3f * density, y + 32f * density); lineTo(x + 15f * density, y + 26f * density); close() }
                    drawPath(tail, IllustrationBackground)
                    drawCircle(green, 1.6f * density, Offset(cx - 8f * density, cy - 1f * density))
                    drawCircle(green, 1.6f * density, Offset(cx, cy - 1f * density))
                    drawCircle(green, 1.6f * density, Offset(cx + 8f * density, cy - 1f * density))
                }
                SetupIllustrationKind.REMINDER -> {
                    val bell = Path().apply {
                        moveTo(cx - 15f * density, cy + 10f * density)
                        cubicTo(cx - 9f * density, cy + 4f * density, cx - 12f * density, cy - 17f * density, cx, cy - 17f * density)
                        cubicTo(cx + 12f * density, cy - 17f * density, cx + 9f * density, cy + 4f * density, cx + 15f * density, cy + 10f * density)
                        close()
                    }
                    drawPath(bell, green, style = Stroke(2f * density))
                    drawLine(green, Offset(cx - 17f * density, cy + 10f * density), Offset(cx + 17f * density, cy + 10f * density), 2f * density)
                    drawArc(green, 200f, 140f, false, Offset(cx - 5f * density, cy + 8f * density), Size(10f * density, 8f * density), style = Stroke(2f * density))
                }
            }
        }
        Surface(
            Modifier.align(Alignment.TopEnd).padding(18.dp),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 2.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) { Text(caption, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun FinishMascotScene(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "mascot-float")
    val bob by transition.animateFloat(-4f, 0f, infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse), label = "mascot-bob")
    Box(
        modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(20.dp)).background(IllustrationBackground),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFDCE8DF), 56f * density, Offset(size.width / 2f, size.height / 2f), style = Stroke(1f * density))
            drawCircle(Color(0xFFE1EBE4), 43f * density, Offset(size.width / 2f, size.height / 2f), style = Stroke(1f * density))
            val cx = size.width / 2f + 85f * density; val cy = size.height / 2f - 30f * density
            drawLine(VaultGreen, Offset(cx, cy - 6f * density), Offset(cx, cy + 6f * density), 1.5f * density)
            drawLine(VaultGreen, Offset(cx - 6f * density, cy), Offset(cx + 6f * density, cy), 1.5f * density)
        }
        VaultMascot(Modifier.size(88.dp, 96.dp).offset(y = bob.dp), withBackground = false)
    }
}

private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction.coerceIn(0f, 1f)
