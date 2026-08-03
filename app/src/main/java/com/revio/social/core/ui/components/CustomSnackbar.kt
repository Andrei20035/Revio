package com.revio.social.core.ui.components

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

/** Background for a positive/success message — see [CustomSnackbar]'s `backgroundColor` param. */
val SnackbarSuccessColor = Color(0xFF2E7D32)

@Composable
fun CustomSnackbar(message: String, backgroundColor: Color = Color(0xFFB00020)) {
    Box(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(16.dp)
            .background(backgroundColor, shape = RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = message,
            color = Color.Companion.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}