package com.pockit.app.ui.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.rememberHaptic
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.components.ChatBox
import com.pockit.app.ui.components.EntryCard
import com.pockit.app.ui.components.MemberChip
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.theme.White

@Composable
fun GroupScreen(
    groupId: Long,
    vm: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val people by vm.people.collectAsState()
    val groups by vm.groups.collectAsState()
    val personMap = remember(people) { people.associateBy { it.id } }

    val entries by vm.getEntriesForGroup(groupId).collectAsState()
    val listState = rememberLazyListState()

    val group = groups.firstOrNull { it.id == groupId }
    var chatText by remember { mutableStateOf(TextFieldValue("")) }
    var filterPersonId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PressableBox(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart), haptic = haptic) {
                Text("←", color = White, fontSize = 20.sp)
            }
            Text(
                text = group?.name ?: "",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(people.filter { !it.isVendor }) { person ->
                MemberChip(
                    name = if (person.isOwner) "My Expenses" else person.mainName,
                    selected = filterPersonId == person.id,
                    onClick = {
                        filterPersonId = if (filterPersonId == person.id) null else person.id
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val displayEntries = if (filterPersonId != null) {
            entries.filter { ews ->
                ews.entry.payerId == filterPersonId ||
                    ews.shares.any { it.personId == filterPersonId }
            }
        } else entries

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(displayEntries, key = { it.entry.id }) { ews ->
                EntryCard(
                    entry = ews.entry,
                    shares = ews.shares,
                    people = personMap,
                    ownerName = "Me",
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        ChatBox(
            value = chatText,
            onValueChange = { chatText = it },
            onSend = { msg ->
                val taggedMsg = if (!msg.contains('@')) "@${group?.name} $msg" else msg
                vm.sendMessage(taggedMsg, groupId)
                chatText = TextFieldValue("")
            },
            haptic = haptic,
        )
    }
}
