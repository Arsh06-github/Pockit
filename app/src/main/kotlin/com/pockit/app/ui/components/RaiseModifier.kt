package com.pockit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Matches theme SurfaceColor / RaisedSurface — duplicated here to keep this
// file free of Composable context at the constant level.
private val FlatBg = Color(0xFF0E0E13)
private val RaisedBg = Color(0xFF17171E)

private val raiseSpring = spring<Float>(
    dampingRatio = 0.65f,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Steering section 5 — Raise modifier.
 *
 * Translates the element -4 dp and scales it to 1.015 when [active] via
 * graphicsLayer (zero layout cost, zero recomposition). The surface colour
 * transition is driven by [raiseColor] below using animateColorAsState, also
 * as required by the spec. Callers must apply .background(raiseColor(active))
 * before this modifier so the colour change is visible.
 */
fun Modifier.raise(active: Boolean): Modifier = composed {
    val density = LocalDensity.current
    val translatePx = with(density) { 4.dp.toPx() }
    val ty = remember { Animatable(0f) }
    val sc = remember { Animatable(1f) }

    LaunchedEffect(active) {
        launch { ty.animateTo(if (active) -translatePx else 0f, raiseSpring) }
        launch { sc.animateTo(if (active) 1.015f else 1f, raiseSpring) }
    }

    this.graphicsLayer {
        translationY = ty.value
        scaleX = sc.value
        scaleY = sc.value
    }
}

/**
 * Returns the animated background colour for a raised element.
 * Usage: Modifier.background(raiseColor(focused)).raise(focused)
 */
@Composable
fun raiseColor(active: Boolean): Color = animateColorAsState(
    targetValue = if (active) RaisedBg else FlatBg,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
    label = "raiseColor",
).value
