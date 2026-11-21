package com.pockit.app.ui.myexpenses

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.rememberHaptic
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.components.EntryCard
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.theme.White
import kotlinx.coroutines.flow.flowOf

@Composable
fun MyExpensesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val people by vm.people.collectAsState()
    val personMap = remember(people) { people.associateBy { it.id } }
    val owner = remember(people) { people.firstOrNull { it.isOwner } }

    val entries by remember(owner) {
        if (owner == null) flowOf(emptyList())
        else vm.repo.observeMyExpenses(owner.id)
    }.collectAsState(initial = emptyList())

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
                text = "My Expenses",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(entries, key = { it.entry.id }) { ews ->
                EntryCard(
                    entry = ews.entry,
                    shares = ews.shares,
                    people = personMap,
                    ownerName = "Me",
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
