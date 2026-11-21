package com.pockit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.theme.DeepLavender
import com.pockit.app.ui.theme.White

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    haptic: HapticHelper? = null,
    hapticStrength: HapticStrength = HapticStrength.MEDIUM,
) {
    val bg = when {
        !enabled -> Color(0x33FFFFFF)
        primary -> DeepLavender
        else -> Color(0x1AFFFFFF)
    }
    val textColor = if (enabled) White else White.copy(alpha = 0.4f)

    PressableBox(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 24.dp, vertical = 0.dp),
        haptic = haptic,
        hapticStrength = hapticStrength,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
