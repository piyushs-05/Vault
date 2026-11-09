package com.piyush.vault.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SpendingDonut(modifier: Modifier = Modifier, centerText: String? = null) {
    val primary = MaterialTheme.colorScheme.primary
    val centerColor = MaterialTheme.colorScheme.surface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ringWidth = size.minDimension * .3f
            val inset = ringWidth / 2f
            val arcSize = Size(size.width - ringWidth, size.height - ringWidth)
            val topLeft = Offset(inset, inset)
            drawArc(primary, -90f, 115f, false, topLeft, arcSize, style = Stroke(ringWidth, cap = StrokeCap.Butt))
            drawArc(Color(0xFFBED7CD), 25f, 126f, false, topLeft, arcSize, style = Stroke(ringWidth, cap = StrokeCap.Butt))
            drawArc(Color(0xFFE8EEE9), 151f, 119f, false, topLeft, arcSize, style = Stroke(ringWidth, cap = StrokeCap.Butt))
            drawCircle(centerColor, radius = size.minDimension * .19f)
        }
        centerText?.let { Text(it, color = primary, style = MaterialTheme.typography.headlineSmall) }
    }
}
