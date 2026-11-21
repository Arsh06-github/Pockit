package com.pockit.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.theme.Motion
import kotlinx.coroutines.launch

@Composable
fun PressableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticHelper? = null,
    hapticStrength: HapticStrength = HapticStrength.LIGHT,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .scale(scale.value)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(Motion.pressScale, Motion.pressBounce) }
                        tryAwaitRelease()
                        scope.launch { scale.animateTo(1f, Motion.pressBounce) }
                    },
                    onTap = {
                        haptic?.perform(hapticStrength)
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
