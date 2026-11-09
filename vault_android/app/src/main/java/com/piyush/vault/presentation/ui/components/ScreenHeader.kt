package com.piyush.vault.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "Navigate back" },
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 1.8.dp.toPx()
                drawLine(iconColor, androidx.compose.ui.geometry.Offset(size.width * .76f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * .24f, size.height / 2), stroke, cap = StrokeCap.Round)
                drawLine(iconColor, androidx.compose.ui.geometry.Offset(size.width * .24f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .26f), stroke, cap = StrokeCap.Round)
                drawLine(iconColor, androidx.compose.ui.geometry.Offset(size.width * .24f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width * .48f, size.height * .74f), stroke, cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(4.dp))
        androidx.compose.foundation.layout.Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}
