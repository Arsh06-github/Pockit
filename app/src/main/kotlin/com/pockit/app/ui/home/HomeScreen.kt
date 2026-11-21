package com.pockit.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.haptics.HapticStrength
import com.pockit.app.haptics.rememberHaptic
import com.pockit.app.ui.AppViewModel
import com.pockit.app.ui.SendResult
import com.pockit.app.ui.components.ChatBox
import com.pockit.app.ui.components.MentionPopup
import com.pockit.app.ui.components.PillNotification
import com.pockit.app.ui.components.PockitWordmark
import com.pockit.app.ui.components.PressableBox
import com.pockit.app.ui.theme.SpaceBlack
import com.pockit.app.ui.theme.White

@Composable
fun HomeScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val groups by vm.groups.collectAsState()
    val sendResult by vm.sendResult.collectAsState()

    var text by remember { mutableStateOf(TextFieldValue("")) }
    var atQuery by remember { mutableStateOf("") }
    var showAtPopup by remember { mutableStateOf(false) }
    var pillText by remember { mutableStateOf<String?>(null) }
    var pillIsError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    // Auto-open keyboard on fresh start
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(sendResult) {
        when (val r = sendResult) {
            is SendResult.Sent -> {
                pillText = "Sent"
                pillIsError = false
                text = TextFieldValue("")
                vm.clearSendResult()
            }
            is SendResult.TypoErrors -> {
                val n = r.lines.size
                pillText = when (n) {
                    1 -> if (r.goodSaved) "1 line had a typo error" else "Typo error"
                    else -> "$n lines had a typo error"
                }
                pillIsError = true
                haptic.perform(HapticStrength.STRONG)
                text = TextFieldValue(r.lines.joinToString("\n"))
                vm.clearSendResult()
            }
            else -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                PressableBox(
                    onClick = {
                        haptic.perform(HapticStrength.LIGHT)
                        vm.openPanel()
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(text = "⋮", color = White, fontSize = 24.sp)
                }
                PockitWordmark(modifier = Modifier.align(Alignment.Center))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = vm.homeMessage,
                    color = White.copy(alpha = 0.18f),
                    fontSize = 18.sp,
                )
            }

            if (showAtPopup) {
                MentionPopup(
                    query = atQuery,
                    groups = groups.map { it.name },
                    onSelect = { tag ->
                        val t = text.text
                        val cursor = text.selection.start
                        val atIdx = t.lastIndexOf('@', cursor - 1).takeIf { it >= 0 } ?: cursor
                        val newText = t.substring(0, atIdx) + tag + " " + t.substring(cursor)
                        text = TextFieldValue(
                            newText,
                            selection = androidx.compose.ui.text.TextRange(atIdx + tag.length + 1),
                        )
                        showAtPopup = false
                        atQuery = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    haptic = haptic,
                )
            }

            ChatBox(
                value = text,
                onValueChange = { text = it },
                onSend = { msg ->
                    vm.sendMessage(msg)
                    showAtPopup = false
                    atQuery = ""
                },
                haptic = haptic,
                focusRequester = focusRequester,
                onAtTyped = { query ->
                    atQuery = query
                    showAtPopup = text.text.contains('@')
                },
            )
        }

        if (pillText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
            ) {
                PillNotification(
                    text = pillText,
                    isError = pillIsError,
                    onDismiss = { pillText = null },
                )
            }
        }
    }
}
