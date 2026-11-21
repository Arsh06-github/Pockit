package com.pockit.app.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticHelper
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.components.DialogButtonsWithCancel
import com.pockit.app.ui.components.PockitDialog
import com.pockit.app.ui.theme.White

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditUserDialog(
    personId: Long,
    vm: AppViewModel,
    onSave: (Long, String, List<String>) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticHelper,
) {
    var mainName by remember { mutableStateOf("") }
    var nickInput by remember { mutableStateOf("") }
    val nicknames = remember { mutableStateListOf<String>() }

    LaunchedEffect(personId) {
        val person = vm.repo.getPersonById(personId)
        mainName = person?.mainName ?: ""
        val nicks = vm.repo.getNicknames(personId)
        nicknames.clear()
        nicknames.addAll(nicks)
    }

    PockitDialog(onDismiss = onDismiss, haptic = haptic) { dismiss ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Edit user", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            NameField(value = mainName, onValueChange = { mainName = it }, placeholder = "Main name")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameField(
                    value = nickInput,
                    onValueChange = { nickInput = it },
                    placeholder = "Add nickname",
                    modifier = Modifier.weight(1f),
                    onDone = {
                        val nick = nickInput.trim().lowercase()
                        if (nick.isNotEmpty() && !nicknames.contains(nick)) {
                            nicknames.add(nick)
                            nickInput = ""
                        }
                    },
                )
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
                primary = "Save details",
                onPrimary = { onSave(personId, mainName.trim(), nicknames.toList()) },
                onCancel = dismiss,
                primaryEnabled = mainName.trim().isNotEmpty(),
                haptic = haptic,
            )
        }
    }
}
