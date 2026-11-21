package com.pockit.app.ui.panel

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.data.GroupSpaceEntity
import com.pockit.app.data.PersonEntity
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.haptics.rememberHaptic
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.Screen
import com.pockit.app.ui.components.PillButton
import com.pockit.app.ui.components.PockitWordmark
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.dialogs.AddUserDialog
import com.pockit.app.ui.dialogs.CreateGroupDialog
import com.pockit.app.ui.dialogs.DeleteConfirmDialog
import com.pockit.app.ui.dialogs.EditGroupDialog
import com.pockit.app.ui.dialogs.EditUserDialog
import com.pockit.app.ui.theme.DividerColor
import com.pockit.app.ui.theme.SpaceBlack
import com.pockit.app.ui.theme.White

sealed class PanelDialog {
    object AddUser : PanelDialog()
    object CreateGroup : PanelDialog()
    data class EditUser(val personId: Long) : PanelDialog()
    data class EditGroup(val groupId: Long) : PanelDialog()
    data class DeleteUser(val personId: Long, val name: String) : PanelDialog()
    data class DeleteGroup(val groupId: Long, val name: String) : PanelDialog()
}

@Composable
fun PanelOverlay(
    open: Boolean,
    vm: AppViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = open,
        enter = androidx.compose.animation.slideInHorizontally(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
            initialOffsetX = { -it }
        ) + androidx.compose.animation.fadeIn(tween(200)),
        exit = androidx.compose.animation.slideOutHorizontally(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
            targetOffsetX = { -it }
        ) + androidx.compose.animation.fadeOut(tween(200)),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Dim scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } }
            )
            // Panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .align(Alignment.CenterStart)
                    .background(SpaceBlack)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -20f) onClose()
                        }
                    },
            ) {
                PanelContent(vm = vm, onClose = onClose)
            }
        }
    }
}

@Composable
private fun PanelContent(vm: AppViewModel, onClose: () -> Unit) {
    val haptic = rememberHaptic()
    val people by vm.people.collectAsState()
    val groups by vm.groups.collectAsState()
    val nickOwners by vm.nickOwners.collectAsState()

    var dialog by remember { mutableStateOf<PanelDialog?>(null) }
    var pillText by remember { mutableStateOf<String?>(null) }

    // Dialogs
    when (val d = dialog) {
        is PanelDialog.AddUser -> AddUserDialog(
            nickOwners = nickOwners,
            onAdd = { name, nicks ->
                vm.addPerson(name, nicks) {
                    dialog = null
                    pillText = "User added"
                }
            },
            onDismiss = { dialog = null },
            haptic = haptic,
        )
        is PanelDialog.EditUser -> EditUserDialog(
            personId = d.personId,
            vm = vm,
            onSave = { id, name, nicks ->
                vm.updatePerson(id, name, nicks) {
                    dialog = null
                    pillText = "Changes saved"
                }
            },
            onDismiss = { dialog = null },
            haptic = haptic,
        )
        is PanelDialog.DeleteUser -> DeleteConfirmDialog(
            message = "Are you sure you want to delete this user? You won't be able to get their details back, and their expenses will not be adjusted anywhere.",
            primaryText = "Yes, delete",
            onConfirm = {
                vm.deletePerson(d.personId) {
                    dialog = null
                    pillText = "${d.name} was deleted"
                }
            },
            onCancel = { dialog = null },
            haptic = haptic,
        )
        is PanelDialog.CreateGroup -> CreateGroupDialog(
            people = people.filter { !it.isVendor },
            existingNames = groups.map { it.name },
            onAdd = { name, memberIds ->
                vm.createGroup(name, memberIds) {
                    dialog = null
                    pillText = "Group created"
                }
            },
            onDismiss = { dialog = null },
            haptic = haptic,
        )
        is PanelDialog.EditGroup -> EditGroupDialog(
            groupId = d.groupId,
            people = people.filter { !it.isVendor },
            existingNames = groups.map { it.name },
            vm = vm,
            onSave = { id, name, memberIds ->
                vm.updateGroup(id, name, memberIds) {
                    dialog = null
                    pillText = "Changes saved"
                }
            },
            onDismiss = { dialog = null },
            haptic = haptic,
        )
        is PanelDialog.DeleteGroup -> DeleteConfirmDialog(
            message = null,
            primaryText = "Yes, delete",
            onConfirm = {
                vm.deleteGroup(d.groupId) {
                    dialog = null
                    pillText = "${d.name} was deleted"
                }
            },
            onCancel = { dialog = null },
            haptic = haptic,
        )
        null -> {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        PockitWordmark(fontSize = 20)
        Spacer(Modifier.height(24.dp))

        Row {
            PillButton(
                text = "Create group",
                onClick = {
                    haptic.perform(HapticStrength.LIGHT)
                    dialog = PanelDialog.CreateGroup
                },
                haptic = haptic,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PillButton(
                text = "Add user",
                onClick = {
                    haptic.perform(HapticStrength.LIGHT)
                    dialog = PanelDialog.AddUser
                },
                haptic = haptic,
                primary = false,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // My Expenses
        PressableBox(
            onClick = {
                vm.navigate(Screen.MyExpenses)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            haptic = haptic,
        ) {
            Text(
                text = "My Expenses",
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor),
        )
        Spacer(Modifier.height(8.dp))

        // Groups
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = "No group created",
                        color = White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
                    GroupRow(
                        group = group,
                        onClick = {
                            vm.navigate(Screen.Group(group.id))
                            onClose()
                        },
                        onEdit = { dialog = PanelDialog.EditGroup(group.id) },
                        onDelete = { dialog = PanelDialog.DeleteGroup(group.id, group.name) },
                        haptic = haptic,
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DividerColor)
                        .padding(vertical = 8.dp),
                )
            }

            // People threads
            items(people.sortedByDescending { it.id }, key = { it.id }) { person ->
                PersonRow(
                    person = person,
                    onClick = {
                        vm.navigate(Screen.Thread(person.id))
                        onClose()
                    },
                    onEdit = { dialog = PanelDialog.EditUser(person.id) },
                    onDelete = { dialog = PanelDialog.DeleteUser(person.id, person.mainName) },
                    haptic = haptic,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupSpaceEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    haptic: com.pockit.app.haptics.HapticHelper,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableBox(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            haptic = haptic,
        ) {
            Text(
                text = group.name,
                color = White,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            )
        }
        PressableBox(
            onClick = {
                haptic.perform(HapticStrength.LIGHT)
                menuOpen = true
            },
            haptic = haptic,
        ) {
            Text(text = "⋮", color = White.copy(alpha = 0.6f), fontSize = 18.sp,
                modifier = Modifier.padding(8.dp))
        }
        if (menuOpen) {
            ThreeDotsMenu(
                items = listOf("Edit group", "Delete group"),
                onSelect = { item ->
                    menuOpen = false
                    when (item) {
                        "Edit group" -> onEdit()
                        "Delete group" -> onDelete()
                    }
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

@Composable
private fun PersonRow(
    person: PersonEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    haptic: com.pockit.app.haptics.HapticHelper,
) {
    if (person.isOwner) return
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableBox(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            haptic = haptic,
        ) {
            Text(
                text = person.mainName,
                color = White,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            )
        }
        PressableBox(
            onClick = {
                haptic.perform(HapticStrength.LIGHT)
                menuOpen = true
            },
            haptic = haptic,
        ) {
            Text(text = "⋮", color = White.copy(alpha = 0.6f), fontSize = 18.sp,
                modifier = Modifier.padding(8.dp))
        }
        if (menuOpen) {
            ThreeDotsMenu(
                items = listOf("Edit user", "Delete user"),
                onSelect = { item ->
                    menuOpen = false
                    when (item) {
                        "Edit user" -> onEdit()
                        "Delete user" -> onDelete()
                    }
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}
