package com.pockit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.theme.BricolageGrotesque
import com.pockit.app.ui.theme.DeepLavender
import com.pockit.app.ui.theme.White

@Composable
fun ChatBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticHelper? = null,
    focusRequester: FocusRequester? = null,
    onAtTyped: ((String) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = raiseColor(focused)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(bg)
                .raise(focused)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (value.text.isEmpty()) {
                Text(
                    text = "Type an expense...",
                    color = White.copy(alpha = 0.4f),
                    fontSize = 16.sp,
                    fontFamily = BricolageGrotesque,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { new ->
                    onValueChange(new)
                    val cursor = new.selection.start
                    val textBefore = new.text.take(cursor)
                    val atIdx = textBefore.lastIndexOf('@')
                    if (atIdx >= 0 && !textBefore.substring(atIdx).contains(' ')) {
                        onAtTyped?.invoke(textBefore.substring(atIdx + 1))
                    } else {
                        onAtTyped?.invoke("")
                    }
                },
                textStyle = TextStyle(
                    color = White,
                    fontSize = 16.sp,
                    fontFamily = BricolageGrotesque,
                    fontWeight = FontWeight.Normal,
                ),
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        Spacer(Modifier.width(8.dp))
        PressableBox(
            onClick = {
                val text = value.text.trim()
                if (text.isNotEmpty()) onSend(text)
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(DeepLavender),
            haptic = haptic,
            hapticStrength = HapticStrength.MEDIUM,
        ) {
            Text(text = "↑", color = White, fontSize = 20.sp)
        }
    }
}
