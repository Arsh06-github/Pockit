package com.pockit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.ui.theme.SunriseOrange
import com.pockit.app.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun PillNotification(
    text: String?,
    isError: Boolean = false,
    onDismiss: () -> Unit,
) {
    var visible by remember(text) { mutableStateOf(text != null) }

    LaunchedEffect(text) {
        if (text != null) {
            visible = true
            delay(2000)
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
    ) {
        val bg = if (isError) SunriseOrange.copy(alpha = 0.15f) else Color(0xFF17171E)
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = text ?: "",
                color = if (isError) SunriseOrange else White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
