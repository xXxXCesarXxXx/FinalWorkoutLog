package com.example.wearabledatasync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun WearableDataSyncTheme( // Note the new theme name
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}