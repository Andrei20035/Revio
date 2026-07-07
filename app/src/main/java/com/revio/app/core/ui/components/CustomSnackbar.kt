package com.revio.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CustomSnackbar(message: String) {
    Box(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xFFB00020), shape = RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = Color.Companion.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}