package com.pockit.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.theme.RaisedSurface
import com.pockit.app.ui.theme.White

@Composable
fun PockitDialog(
    onDismiss: () -> Unit,
    haptic: HapticHelper? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .scale(scale.value)
                .alpha(alpha.value)
                .clip(RoundedCornerShape(24.dp))
                .background(RaisedSurface)
                .padding(24.dp),
        ) {
            content(onDismiss)
        }
    }
}

@Composable
fun DialogButtonsWithCancel(
    primary: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    primaryEnabled: Boolean = true,
    haptic: HapticHelper? = null,
    primaryIsDestructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableBox(
            onClick = onCancel,
            haptic = haptic,
            hapticStrength = HapticStrength.LIGHT,
        ) {
            Text(
                text = "Cancel",
                color = White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        PillButton(
            text = primary,
            onClick = onPrimary,
            enabled = primaryEnabled,
            haptic = haptic,
            hapticStrength = if (primaryIsDestructive) HapticStrength.STRONG else HapticStrength.MEDIUM,
        )
    }
}
