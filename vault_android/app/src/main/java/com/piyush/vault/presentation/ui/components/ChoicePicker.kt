package com.piyush.vault.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun <T> ChoicePicker(label: String, value: T, choices: List<Pair<T,String>>, onChoose: (T)->Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(choices.firstOrNull { it.first == value }?.second ?: "Choose")
        }
        DropdownMenu(expanded = expanded,onDismissRequest = { expanded = false }) {
            choices.forEach { (key,name) ->
                DropdownMenuItem(text = { Text(name) },onClick = { onChoose(key); expanded = false })
            }
        }
    }
}
