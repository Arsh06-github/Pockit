package com.pockit.app.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.theme.RaisedSurface
import com.pockit.app.ui.theme.White

@Composable
fun ThreeDotsMenu(
    items: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RaisedSurface)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { item ->
                PressableBox(
                    onClick = { onSelect(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(text = item, color = White, fontSize = 14.sp)
                }
            }
        }
    }
}
