// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.authority.*
import org.plainphone.shell.PreviewPhone
import org.plainphone.shell.NotInBuildScreen
import org.plainphone.ui.home.*
import org.plainphone.ui.theme.*
import org.plainphone.ui.trusted.*

/** Private preview controls. This activity grants no access to Android settings or other apps. */
class PinAccessActivity : ComponentActivity() {
    private var page by mutableStateOf(PinPageState())
    private var value by mutableStateOf("")
    private var confirmation by mutableStateOf("")
    private val runtime by lazy { PolicyRuntime.get(this) }
    private val flow by lazy {
        PinScreenFlow(object : PinScreenPort {
            override fun entry(done: (PinEntry) -> Unit) = runtime.pinEntry { runOnUiThread { done(it) } }
            override fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit) =
                runtime.enrollPin(pin) { runOnUiThread { done(it) } }
            override fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit) =
                runtime.checkPin(pin) { runOnUiThread { done(it) } }
        }) { page = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BackHandler { if (page.page == PinPage.NOT_IN_BUILD) flow.backFromUnsupported() else finish() }
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    when (page.page) {
                        PinPage.LOADING -> Unit
                        PinPage.SETUP, PinPage.ENTER, PinPage.LOCKED -> PinScreen(PinUiState(
                            context = AdminContext(PreviewPhone.sample().userName),
                            stage = when (page.page) {
                                PinPage.SETUP -> PinStage.NewPin
                                PinPage.LOCKED -> PinStage.Locked
                                else -> PinStage.Enter
                            },
                            value = value, confirmation = confirmation,
                            canContinue = value.length >= 6 && value.all { it in '0'..'9' } &&
                                (page.page != PinPage.SETUP || value == confirmation),
                            sending = page.busy, wrongPin = page.wrong,
                            wait = DateUtils.formatElapsedTime((page.waitMillis + 999) / 1000),
                        )) { event -> when (event) {
                            is PinEvent.ValueChanged -> value = event.value
                            is PinEvent.ConfirmationChanged -> confirmation = event.value
                            PinEvent.Continue -> {
                                val pin = value.toCharArray()
                                value = ""; confirmation = ""
                                flow.submit(pin)
                            }
                            PinEvent.UseRecoveryCode -> { value = ""; confirmation = ""; flow.unsupported() }
                            else -> finish()
                        } }
                        PinPage.MENU -> AdminMenuScreen(AdminMenuUiState(
                            AdminContext(PreviewPhone.sample().userName), AdminDestination.entries.map { AdminMenuItem(it) },
                        )) { if (it == AdminMenuEvent.Done) finish() else flow.unsupported() }
                        PinPage.NOT_IN_BUILD -> NotInBuildScreen(flow::backFromUnsupported)
                        PinPage.UNAVAILABLE -> NeedsAttentionScreen(NeedsAttentionUiState(null)) { finish() }
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); flow.open() }
    override fun onPause() {
        value = ""; confirmation = ""
        flow.close()
        super.onPause()
    }
}
