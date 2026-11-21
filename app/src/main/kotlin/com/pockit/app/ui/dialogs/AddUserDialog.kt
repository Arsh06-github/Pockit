package com.pockit.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.ui.components.DialogButtonsWithCancel
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.components.raise
import com.pockit.app.ui.components.raiseColor
import com.pockit.app.ui.theme.BricolageGrotesque
import com.pockit.app.ui.theme.SunriseOrange
import com.pockit.app.ui.theme.SurfaceColor
import com.pockit.app.ui.theme.White

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddUserDialog(
    nickOwners: Map<String, String>,
    onAdd: (mainName: String, nicknames: List<String>) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticHelper,
) {
    var mainName by remember { mutableStateOf("") }
    var nickInput by remember { mutableStateOf("") }
    val nicknames = remember { mutableStateListOf<String>() }
    var duplicateMsg by remember { mutableStateOf<String?>(null) }

    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Add user", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            NameField(
                value = mainName,
                onValueChange = { mainName = it },
                placeholder = "Main name",
            )

            Spacer(Modifier.height(12.dp))

            // Nicknames input
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameField(
                    value = nickInput,
                    onValueChange = { nickInput = it; duplicateMsg = null },
                    placeholder = "Add nickname",
                    modifier = Modifier.weight(1f),
                    onDone = {
                        val nick = nickInput.trim().lowercase()
                        if (nick.isNotEmpty()) {
                            val ownerName = nickOwners[nick]
                            when {
                                ownerName != null ->
                                    duplicateMsg = "This nickname belongs to $ownerName"
                                nicknames.contains(nick) ->
                                    duplicateMsg = "Already added"
                                else -> { nicknames.add(nick); nickInput = "" }
                            }
                        }
                    },
                )
            }

            duplicateMsg?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("● ", color = SunriseOrange, fontSize = 12.sp)
                    Text(it, color = White, fontSize = 13.sp)
                }
            }

            if (nicknames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow {
                    nicknames.forEach { nick ->
                        NickChip(nick = nick, onRemove = { nicknames.remove(nick) })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            DialogButtonsWithCancel(
                primary = "Add user",
                onPrimary = { onAdd(mainName.trim(), nicknames.toList()) },
                onCancel = dismiss,
                primaryEnabled = mainName.trim().isNotEmpty(),
                haptic = haptic,
            )
        }
    }
}

@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = raiseColor(focused)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = White,
            fontSize = 16.sp,
            fontFamily = BricolageGrotesque,
        ),
        cursorBrush = SolidColor(White),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .raise(focused)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = White.copy(alpha = 0.4f), fontSize = 16.sp)
            inner()
        },
    )
}

@Composable
fun NickChip(nick: String, onRemove: () -> Unit) {
    PressableBox(
        onClick = onRemove,
        modifier = Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(CircleShape)
            .background(SurfaceColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$nick ×",
            color = White.copy(alpha = 0.8f),
            fontSize = 13.sp,
        )
    }
}
