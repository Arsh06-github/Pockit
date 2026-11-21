package com.pockit.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.data.PersonEntity
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.components.DialogButtonsWithCancel
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.theme.SunriseOrange
import com.pockit.app.ui.theme.White
import kotlinx.coroutines.flow.first

private val RESERVED_NAMES = setOf("general", "search", "edit")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditGroupDialog(
    groupId: Long,
    people: List<PersonEntity>,
    existingNames: List<String>,
    vm: AppViewModel,
    onSave: (Long, String, List<Long>) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticHelper,
) {
    var groupName by remember { mutableStateOf("") }
    var originalName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        val group = vm.repo.observeAliveGroups().first().firstOrNull { it.id == groupId }
        groupName = group?.name ?: ""
        originalName = group?.name ?: ""
        val members = vm.repo.getActiveGroupMembers(groupId)
        selectedIds.clear()
        selectedIds.addAll(members)
    }

    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Edit group", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            NameField(
                value = groupName,
                onValueChange = { groupName = it; errorMsg = null },
                placeholder = "Group name",
            )
            errorMsg?.let { Text(it, color = SunriseOrange, fontSize = 13.sp) }
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
                            if (selected) selectedIds.remove(person.id) else selectedIds.add(person.id)
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            DialogButtonsWithCancel(
                primary = "Save details",
                onPrimary = {
                    val name = groupName.trim()
                    when {
                        name.lowercase() in RESERVED_NAMES -> errorMsg = "Name is reserved"
                        existingNames.any { it.equals(name, ignoreCase = true) && !it.equals(originalName, ignoreCase = true) } ->
                            errorMsg = "A group with this name already exists"
                        else -> onSave(groupId, name, selectedIds.toList())
                    }
                },
                onCancel = dismiss,
                primaryEnabled = groupName.trim().isNotEmpty(),
                haptic = haptic,
            )
        }
    }
}
