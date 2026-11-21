package com.pockit.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.data.PersonEntity
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.components.DialogButtonsWithCancel
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.theme.DeepLavender
import com.pockit.app.ui.theme.SunriseOrange
import com.pockit.app.ui.theme.SurfaceColor
import com.pockit.app.ui.theme.White

private val RESERVED = setOf("general", "search", "edit")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupDialog(
    people: List<PersonEntity>,
    existingNames: List<String>,
    onAdd: (name: String, memberIds: List<Long>) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticHelper,
) {
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember {
        mutableStateListOf<Long>().also { list ->
            people.firstOrNull { it.isOwner }?.let { list.add(it.id) }
        }
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Create group", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            NameField(
                value = groupName,
                onValueChange = {
                    groupName = it
                    errorMsg = null
                },
                placeholder = "Group name",
            )
            errorMsg?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = SunriseOrange, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Members", color = White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow {
                people.forEach { person ->
                    val selected = selectedIds.contains(person.id)
                    MemberChip(
                        name = if (person.isOwner) "Me" else person.mainName,
                        selected = selected,
                        onClick = {
                            haptic.perform(HapticStrength.LIGHT)
                            if (selected) selectedIds.remove(person.id)
                            else selectedIds.add(person.id)
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DialogButtonsWithCancel(
                primary = "Create group",
                onPrimary = {
                    val name = groupName.trim()
                    when {
                        name.lowercase() in RESERVED -> errorMsg = "Name is reserved"
                        existingNames.any { it.equals(name, ignoreCase = true) } ->
                            errorMsg = "A group with this name already exists"
                        else -> onAdd(name, selectedIds.toList())
                    }
                },
                onCancel = dismiss,
                primaryEnabled = groupName.trim().isNotEmpty(),
                haptic = haptic,
            )
        }
    }
}

@Composable
fun MemberChip(name: String, selected: Boolean, onClick: () -> Unit) {
    PressableBox(
        onClick = onClick,
        modifier = Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .clip(CircleShape)
            .background(if (selected) DeepLavender else SurfaceColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            color = White,
            fontSize = 14.sp,
        )
    }
}
