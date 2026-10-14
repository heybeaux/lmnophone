// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.*
import org.plainphone.device.requests.RequestEngine
import org.plainphone.policy.*
import org.plainphone.ui.requests.*
import org.plainphone.ui.theme.*
import java.util.UUID

/** Non-exported U5/T3 host. Only an explicit Send invokes the outbox. */
class RequestsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        val engine = RequestEngine.get(this)
        val historyOnly = intent.getBooleanExtra(HISTORY, false)
        setContent {
            var view by remember { mutableStateOf(RequestView(false, "", emptyList())) }
            var removingId by remember { mutableStateOf<String?>(null) }
            var removeFailed by remember { mutableStateOf(false) }
            var draft by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var submission by remember { mutableStateOf<RequestSubmission?>(null) }
            var id by remember { mutableStateOf(UUID.randomUUID().toString()) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                while (true) {
                    if (removingId == null && !busy) view = withContext(Dispatchers.IO) { engine.view() }
                    delay(1_000)
                }
            }
            BackHandler {
                if (!busy) {
                    if (removingId != null) { removingId = null; removeFailed = false } else finish()
                }
            }
            val result = submission?.let { sent -> view.requests.firstOrNull { it.id == sent.id }?.status ?: sent.status }
            val state = RequestsUiMapper.map(view, draft, result, busy, historyOnly).copy(
                userName = getString(org.plainphone.ui.R.string.current_phone_user),
                removingId = removingId, removeFailed = removeFailed)
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    RequestsScreen(state) { event -> when(event) {
                        is RequestEvent.NotNow -> if (historyOnly && !busy && view.available && view.requests.any { it.id == event.id }) {
                            removingId = event.id; removeFailed = false
                        }
                        RequestEvent.Keep -> if (!busy) { removingId = null; removeFailed = false }
                        RequestEvent.Remove -> if (historyOnly && !busy && removingId != null) {
                            val target = removingId!!
                            busy = true
                            scope.launch {
                                try {
                                    val removed = withContext(Dispatchers.IO) { engine.remove(target) }
                                    removeFailed = !removed
                                    if (removed) {
                                        view = withContext(Dispatchers.IO) { engine.view() }
                                        removingId = null
                                    }
                                } finally { busy = false }
                            }
                        }
                        RequestEvent.Back -> if (!busy) finish()
                        is RequestEvent.Message -> if (!busy && event.text.length <= 280) draft = event.text
                        RequestEvent.Compose -> if (!busy) { submission = null; draft = ""; id = UUID.randomUUID().toString() }
                        RequestEvent.Send -> if (!historyOnly && !busy && submission == null && state.canSend) {
                            busy = true
                            val attempt = id; val text = draft
                            scope.launch {
                                try {
                                    submission = withContext(Dispatchers.IO) { engine.submit(attempt, text) }
                                    view = withContext(Dispatchers.IO) { engine.view() }
                                } finally { busy = false }
                            }
                        }
                    } }
                }
            }
        }
    }
    companion object { const val HISTORY = "history" }
}
