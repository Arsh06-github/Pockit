package com.pockit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.theme.DeepLavender
import com.pockit.app.ui.theme.RaisedSurface
import com.pockit.app.ui.theme.White

data class MentionRow(val tag: String, val displayName: String)

@Composable
fun MentionPopup(
    query: String,
    groups: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    haptic: HapticHelper? = null,
) {
    val allRows = remember(groups) {
        listOf(
            MentionRow("@Search", "@Search"),
            MentionRow("@Edit", "@Edit"),
            MentionRow("@General", "@General"),
        ) + groups.map { MentionRow("@$it", "@$it") }
    }
    val filtered by remember(query, allRows) {
        derivedStateOf {
            if (query.isEmpty()) allRows
            else allRows.filter { it.tag.lowercase().contains(query.lowercase()) }
        }
    }

    if (filtered.isEmpty()) return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RaisedSurface)
            .padding(vertical = 4.dp),
    ) {
        filtered.forEachIndexed { index, row ->
            val isHighlighted = index == 0
            PressableBox(
                onClick = {
                    haptic?.perform(HapticStrength.LIGHT)
                    onSelect(row.tag)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isHighlighted) DeepLavender else Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = row.displayName,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
