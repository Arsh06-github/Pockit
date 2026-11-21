package com.pockit.app.haptics

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticStrength { LIGHT, MEDIUM, STRONG }

class HapticHelper(private val view: View) {
    fun perform(strength: HapticStrength) {
        when (strength) {
            HapticStrength.LIGHT -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            HapticStrength.MEDIUM -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            HapticStrength.STRONG -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }
    }
}

@Composable
fun rememberHaptic(): HapticHelper {
    val view = LocalView.current
    return remember(view) { HapticHelper(view) }
}
