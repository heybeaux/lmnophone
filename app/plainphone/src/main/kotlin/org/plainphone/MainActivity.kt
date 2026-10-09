// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay
import org.plainphone.shell.PlainphoneShell
import org.plainphone.shell.PreviewPhone
import org.plainphone.shell.ShellEffect
import org.plainphone.shell.ShellNavigator
import org.plainphone.shell.ShellState
import org.plainphone.shell.ShellUiMapper
import org.plainphone.shell.Step
import org.plainphone.ui.theme.PlainphoneColors

/**
 * Preview shell: the `:ui` screens fed by [PreviewPhone]. A normal app with no device authority;
 * later D1 slices swap the fake for `:device` and make this the home activity.
 */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        shell.start(if (debuggable) intent.getStringExtra(EXTRA_PREVIEW_SCREEN) else null)
        val mapper = ShellUiMapper(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
        setContent {
            var snapshot by remember { mutableStateOf(shell.phone.snapshot()) }
            LaunchedEffect(shell.state) {
                while (true) {
                    snapshot = shell.phone.snapshot()
                    delay(1_000)
                }
            }
            PlainphoneShell(shell.state, snapshot, mapper, shell.navigator) { step ->
                shell.state = step.state
                step.effect?.let(::perform)
            }
        }
    }

    private fun perform(effect: ShellEffect) {
        val intent = when (effect) {
            ShellEffect.OpenDialer -> Intent(Intent.ACTION_DIAL)
            ShellEffect.OpenMessages ->
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            ShellEffect.OpenEmergencyDialer -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
            is ShellEffect.OpenApp -> packageManager.getLaunchIntentForPackage(effect.id)
            ShellEffect.Finish -> null.also { finish() }
        } ?: return
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Preview: an app missing from this emulator just doesn't open.
        }
    }

    companion object {
        /** Debug builds only: `more`, `blocked_link`, `wifi_sign_in` or `wifi_no_internet`. */
        const val EXTRA_PREVIEW_SCREEN = "org.plainphone.preview.SCREEN"
    }
}

/** Keeps the back stack and the fake phone across configuration changes such as text size. */
class ShellViewModel : ViewModel() {
    val phone = PreviewPhone(PreviewPhone.sample())
    val navigator = ShellNavigator(phone, phone)
    var state by mutableStateOf(ShellState())
    private var started = false

    fun start(entry: String?) {
        if (started) return
        started = true
        state = ShellState.forPreviewEntry(entry)
    }
}
