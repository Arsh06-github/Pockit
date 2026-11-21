package com.pockit.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.data.EntryWithShares
import com.pockit.app.data.PersonEntity
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.haptics.rememberHaptic
import com.pockit.app.ui.components.EntryCard
import com.pockit.app.ui.components.PillButton
import com.pockit.app.ui.components.PockitBottomSheet
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.components.raise
import com.pockit.app.ui.components.raiseColor
import com.pockit.app.ui.dialogs.DeleteConfirmDialog
import com.pockit.app.ui.group.GroupScreen
import com.pockit.app.ui.home.HomeScreen
import com.pockit.app.ui.myexpenses.MyExpensesScreen
import com.pockit.app.ui.panel.PanelOverlay
import com.pockit.app.ui.thread.ThreadScreen
import com.pockit.app.ui.theme.SpaceBlack
import com.pockit.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PockitApp(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
    val panelOpen by vm.panelOpen.collectAsState()
    val sheetContent by vm.sheetContent.collectAsState()
    val pendingDelete by vm.pendingDelete.collectAsState()
    val pendingClear by vm.pendingClear.collectAsState()
    val haptic = rememberHaptic()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack),
    ) {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(vm = vm, modifier = Modifier.fillMaxSize())
            is Screen.Thread -> {
                BackHandler { vm.navigate(Screen.Home) }
                ThreadScreen(
                    personId = s.personId,
                    vm = vm,
                    onBack = { vm.navigate(Screen.Home) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is Screen.Group -> {
                BackHandler { vm.navigate(Screen.Home) }
                GroupScreen(
                    groupId = s.groupId,
                    vm = vm,
                    onBack = { vm.navigate(Screen.Home) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is Screen.MyExpenses -> {
                BackHandler { vm.navigate(Screen.Home) }
                MyExpensesScreen(
                    vm = vm,
                    onBack = { vm.navigate(Screen.Home) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        BackHandler(enabled = panelOpen) { vm.closePanel() }
        PanelOverlay(open = panelOpen, vm = vm, onClose = { vm.closePanel() })

        BackHandler(enabled = sheetContent != null) { vm.closeSheet() }
        PockitBottomSheet(
            visible = sheetContent != null,
            onDismiss = { vm.closeSheet() },
        ) {
            SheetBody(content = sheetContent)
        }

        pendingDelete?.let { entry ->
            DeleteConfirmDialog(
                message = "Delete this entry? Dues will be recalculated.",
                primaryText = "Yes, delete",
                onConfirm = { vm.confirmDelete(entry.id) {} },
                onCancel = { vm.clearPendingDelete() },
                haptic = haptic,
            )
        }

        val pendingEditPickOne by vm.pendingEditPickOne.collectAsState()
        pendingEditPickOne?.let { pending ->
            EditPickOneDialog(
                pending = pending,
                onPick = { entryId -> vm.confirmEditPickOne(entryId) },
                onDismiss = { vm.clearPendingEditPickOne() },
                haptic = haptic,
            )
        }

        pendingClear?.let { pending ->
            ClearConfirmDialog(
                pending = pending,
                onSettled = { vm.confirmClearSettled {} },
                onCarryForward = { vm.confirmClearCarryForward {} },
                onDismiss = { vm.clearPendingClear() },
                haptic = haptic,
            )
        }
    }
}

@Composable
private fun SheetBody(content: SheetContent?) {
    when (content) {
        null -> {}
        is SheetContent.NothingPending ->
            Text("Nothing pending", color = White, fontSize = 16.sp)

        is SheetContent.NothingFound ->
            Text("Nothing found", color = White, fontSize = 16.sp)

        is SheetContent.SearchResults -> {
            Text("Results", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(content.entries, key = { it.entry.id }) { ews ->
                    ExpandableEntryRow(ews = ews, peopleMap = content.peopleMap)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        is SheetContent.SettleUp -> {
            Text("Settle up", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content.payments.forEach { p ->
                val from = content.peopleMap[p.fromId]
                    ?.let { if (it.isOwner) "Me" else it.mainName } ?: "?"
                val to = content.peopleMap[p.toId]
                    ?.let { if (it.isOwner) "Me" else it.mainName } ?: "?"
                Text(
                    text = "$from pays $to ₹${"%,d".format(p.amount)}",
                    color = White,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        is SheetContent.BalanceTwo -> {
            val header = if (content.net > 0)
                "${content.personAName} owes ${content.personBName} ₹${"%,d".format(content.net)}"
            else
                "${content.personBName} owes ${content.personAName} ₹${"%,d".format(-content.net)}"
            Text(header, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (content.entries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(content.entries, key = { it.entry.id }) { ews ->
                        ExpandableEntryRow(ews = ews, peopleMap = emptyMap())
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        is SheetContent.BalanceOne -> {
            Text(
                content.personName,
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "General: ₹${"%,d".format(content.generalNet)}",
                color = White,
                fontSize = 15.sp,
            )
        }

        is SheetContent.EntryTable -> {
            Text("Expenses", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(content.entries, key = { it.entry.id }) { ews ->
                    ExpandableEntryRow(ews = ews, peopleMap = content.peopleMap)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandableEntryRow(ews: EntryWithShares, peopleMap: Map<Long, PersonEntity>) {
    var expanded by remember(ews.entry.id) { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
    val dateStr = LocalDate.ofEpochDay(ews.entry.expenseDate).format(dateFmt)
    val bg = raiseColor(expanded)

    PressableBox(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .raise(expanded)
            .padding(12.dp),
    ) {
        if (expanded) {
            EntryCard(
                entry = ews.entry,
                shares = ews.shares,
                people = peopleMap,
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${ews.entry.label} - ${ews.entry.labelNumber}",
                    color = White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = dateStr,
                    color = White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    text = "₹${"%,d".format(ews.entry.total)}",
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ClearConfirmDialog(
    pending: PendingClear,
    onSettled: () -> Unit,
    onCarryForward: () -> Unit,
    onDismiss: () -> Unit,
    haptic: com.pockit.app.haptics.HapticHelper,
) {
    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            if (pending.pairNets.isEmpty()) {
                Text("Nothing pending", color = White, fontSize = 15.sp)
            } else {
                Text(
                    "Already settled?",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                pending.pairNets.forEach { (pair, net) ->
                    val nameA = pending.peopleMap[pair.first]
                        ?.let { if (it.isOwner) "Me" else it.mainName } ?: "?"
                    val nameB = pending.peopleMap[pair.second]
                        ?.let { if (it.isOwner) "Me" else it.mainName } ?: "?"
                    Text(
                        text = "Net: $nameA owes $nameB ₹${"%,d".format(net)}",
                        color = White,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row {
                PressableBox(
                    onClick = dismiss,
                    haptic = haptic,
                    hapticStrength = HapticStrength.LIGHT,
                ) {
                    Text(
                        "Cancel",
                        color = White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                PillButton(
                    text = "No, carry forward",
                    onClick = onCarryForward,
                    primary = false,
                    haptic = haptic,
                    hapticStrength = HapticStrength.MEDIUM,
                )
                Spacer(Modifier.width(8.dp))
                PillButton(
                    text = "Yes, settled",
                    onClick = onSettled,
                    haptic = haptic,
                    hapticStrength = HapticStrength.MEDIUM,
                )
            }
        }
    }
}

@Composable
private fun EditPickOneDialog(
    pending: PendingEditPickOne,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    haptic: com.pockit.app.haptics.HapticHelper,
) {
    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Which entry?",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            pending.candidates.forEach { ews ->
                val dateFmt = remember { java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH) }
                val dateStr = java.time.LocalDate.ofEpochDay(ews.entry.expenseDate).format(dateFmt)
                var selected by remember(ews.entry.id) { mutableStateOf(false) }
                val bg = raiseColor(selected)
                PressableBox(
                    onClick = {
                        selected = true
                        onPick(ews.entry.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .raise(selected)
                        .padding(12.dp),
                    haptic = haptic,
                    hapticStrength = HapticStrength.LIGHT,
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${ews.entry.label} - ${ews.entry.labelNumber}",
                            color = White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = dateStr,
                            color = White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "₹${"%,d".format(ews.entry.total)}",
                            color = White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(12.dp))
            PressableBox(onClick = dismiss, haptic = haptic) {
                Text("Cancel", color = White.copy(alpha = 0.6f), fontSize = 15.sp)
            }
        }
    }
}
