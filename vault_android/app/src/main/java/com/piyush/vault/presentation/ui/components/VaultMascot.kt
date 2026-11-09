package com.piyush.vault.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun VaultMascot(
    modifier: Modifier = Modifier.size(30.dp),
    withBackground: Boolean = true,
) {
    val canvasModifier = if (withBackground) modifier.clip(RoundedCornerShape(9.dp)) else modifier
    Canvas(canvasModifier) {
        val sx = size.width / 120f
        val sy = size.height / 130f
        fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
        if (withBackground) {
            drawRoundRect(Color(0xFFEAF3EE), cornerRadius = CornerRadius(size.minDimension * .3f))
        }
        drawLine(Color(0xFF123C31), point(39f, 105f), point(39f, 113f), 4f * sx, cap = StrokeCap.Round)
        drawLine(Color(0xFF123C31), point(77f, 105f), point(77f, 113f), 4f * sx, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF176B58), point(16f, 23f), androidx.compose.ui.geometry.Size(82f * sx, 82f * sy), CornerRadius(25f * sx))
        drawRoundRect(Color(0xFFE5F1EB), point(27f, 34f), androidx.compose.ui.geometry.Size(60f * sx, 60f * sy), CornerRadius(19f * sx))
        drawCircle(Color(0xFF123C31), 3.4f * sx, point(47f, 59f))
        drawCircle(Color(0xFF123C31), 3.4f * sx, point(68f, 59f))
        drawCircle(Color(0xFFD9B86C), 4f * sx, point(76f, 83f))
    }
}
