package com.pockit.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.ui.components.DialogButtonsWithCancel
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.theme.White

@Composable
fun DeleteConfirmDialog(
    message: String?,
    primaryText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    haptic: HapticHelper,
) {
    PockitDialog(onDismiss = onCancel, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            message?.let {
                Text(it, color = White, fontSize = 15.sp)
                Spacer(Modifier.height(20.dp))
            }
            DialogButtonsWithCancel(
                primary = primaryText,
                onPrimary = onConfirm,
                onCancel = dismiss,
                haptic = haptic,
                primaryIsDestructive = true,
            )
        }
    }
}
