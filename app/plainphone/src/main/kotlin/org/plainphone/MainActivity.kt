// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.KeyguardManager
import android.content.ComponentName
import androidx.compose.runtime.DisposableEffect
import android.media.AudioManager
import org.plainphone.device.settings.AndroidVolume
import org.plainphone.device.settings.VolumeController
import org.plainphone.device.settings.AndroidBluetoothDiscovery
import org.plainphone.device.settings.BluetoothDiscoveryController
import org.plainphone.ui.user.BluetoothDiscoveryUiState
import org.plainphone.ui.user.NearbyBluetoothOption
import org.plainphone.ui.user.BluetoothDiscoveryEvent
import org.plainphone.device.settings.AndroidBluetooth
import org.plainphone.device.settings.BluetoothController
import org.plainphone.ui.user.BluetoothSettingsUiState
import org.plainphone.device.settings.WifiJoinSession
import org.plainphone.device.settings.WifiSecurity
import org.plainphone.ui.user.WifiJoinUiState
import org.plainphone.ui.user.WifiJoinKind
import org.plainphone.ui.user.WifiJoinEvent
import org.plainphone.ui.user.WifiNearbyUi
import org.plainphone.device.settings.WifiController
import org.plainphone.device.settings.WifiInternetStatus
import org.plainphone.ui.user.WifiInternetUiStatus
import org.plainphone.ui.user.WifiSettingsUiState
import org.plainphone.device.settings.AndroidRingtone
import org.plainphone.device.settings.RingtoneController
import org.plainphone.ui.user.RingtoneUiState
import org.plainphone.ui.user.RingtoneOption
import org.plainphone.ui.user.RingtoneEvent
import org.plainphone.device.settings.AndroidTextSize
import org.plainphone.device.settings.TextSizeController
import org.plainphone.ui.user.TextSizeUiState
import org.plainphone.ui.user.TextSizeEvent
import java.math.BigDecimal
import org.plainphone.ui.user.SoundUiState
import org.plainphone.ui.user.SoundStream
import org.plainphone.ui.user.SoundEvent
import org.plainphone.ui.user.VolumeUiState
import org.plainphone.device.settings.AndroidBrightness
import org.plainphone.device.settings.BrightnessController
import org.plainphone.settings.BatterySource
import org.plainphone.ui.user.BatteryUiState
import org.plainphone.ui.user.BrightnessUiState
import org.plainphone.ui.user.BrightnessEvent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.SideEffect
import org.plainphone.counts.CommunicationEntryIntents
import org.plainphone.counts.AndroidCommunicationCounts
import org.plainphone.counts.CountAvailability
import org.plainphone.counts.CountKind
import org.plainphone.counts.CommunicationCounts
import org.plainphone.shell.ShellRoute
import org.plainphone.ui.user.PhoneSettingsUiState
import org.plainphone.ui.user.PhoneSettingsEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import org.plainphone.boot.BuildControl
import org.plainphone.boot.CallingScreen
import org.plainphone.boot.LockTaskState
import org.plainphone.boot.checkingShowsAttention
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.FallbackNotice
import org.plainphone.boot.HomeGate
import org.plainphone.boot.HomeScreens
import org.plainphone.boot.cancelStage
import org.plainphone.boot.confirmStage
import org.plainphone.boot.settingsWaitingChange
import org.plainphone.boot.waitingChangeTextResource
import org.plainphone.boot.pendingChange
import org.plainphone.policy.NoticeTap
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.ui.home.NeedsAttentionEvent
import org.plainphone.ui.home.NeedsAttentionScreen
import org.plainphone.ui.home.NeedsAttentionUiState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.plainphone.boot.PolicyRuntime
import org.plainphone.policy.NoticeInput
import org.plainphone.preview.Preview2Device
import org.plainphone.shell.ApprovedApp
import org.plainphone.shell.PlainphoneShell
import org.plainphone.shell.PreviewPhone
import org.plainphone.shell.ShellEffect
import org.plainphone.shell.ShellNavigator
import org.plainphone.shell.ShellState
import org.plainphone.shell.ShellUiMapper
import org.plainphone.shell.Step
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.WaitingChangeEvent
import org.plainphone.ui.trusted.WaitingChangeScreen
import org.plainphone.ui.trusted.WaitingChangeUiState
import org.plainphone.ui.trusted.WaitingStage
import org.plainphone.device.voice.VoicePlace
import org.plainphone.voice.VoicePracticeHost

/**
 * Plainphone Home. Keyguard first (PLA-264 finding 1): the manifest never starts lock task; Home calls
 * `startLockTask()` itself, and only when [PolicyRuntime.mayEnterLockTask] reads the features back with
 * KEYGUARD. It tries on create, resume, new intent (the boot receiver's relaunch) and after every
 * re-apply run. Home draws only when [PolicyRuntime.gate] is READY (the full re-apply read back) and
 * the task is in lock task; until then the neutral checking screen shows, with only Phone and Emergency.
 * Lock task must actually be running (PLA-307 finding 2): create and resume read the live state, and
 * the runtime brings Home forward after a ready pass that finds it not running ([LockTaskRecovery]).
 * Only LOCKED counts; PINNED is not lock task (PLA-322 finding 3). Once LOCKED reads back, Home reopens
 * the calling screen recovery displaced, inside lock task (PLA-322 finding 1).
 */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()
    private lateinit var communication: AndroidCommunicationCounts
    private var discovery by mutableStateOf(BluetoothDiscoveryUiState())
    private val discoverySource by lazy { AndroidBluetoothDiscovery(this) }
    private val discoveryController by lazy { BluetoothDiscoveryController(discoverySource) { value ->
        discovery = BluetoothDiscoveryUiState(value.looking,
            value.devices.map { NearbyBluetoothOption(it.id, it.name) }, value.failed)
    } }
    private fun openDiscovery() {
        if (!resumed || shell.state.current != ShellRoute.BluetoothSettings) return
        syncBluetooth()
        if (bluetooth.enabled && !bluetooth.readFailed)
            shell.state = shell.state.copy(stack = shell.state.stack + ShellRoute.BluetoothDiscovery)
    }
    private fun onDiscovery(event: BluetoothDiscoveryEvent) {
        if (!resumed || shell.state.current != ShellRoute.BluetoothDiscovery) return
        when (event) {
            BluetoothDiscoveryEvent.Back -> shell.state = shell.navigator!!.back(shell.state).state
            BluetoothDiscoveryEvent.LookAgain -> if (!discovery.looking && !discovery.failed) discoveryController.open()
            is BluetoothDiscoveryEvent.Select -> {
                discoveryController.checkAvailable()
                val selected = discoveryController.state.devices.firstOrNull { it.id == event.id } ?: return
                shell.state = shell.state.copy(stack = shell.state.stack + ShellRoute.BluetoothDevice(selected.name))
            }
        }
    }
    private val bluetoothSource by lazy { AndroidBluetooth(this) }
    private val bluetoothController by lazy { BluetoothController(bluetoothSource) }
    private var bluetooth by mutableStateOf(BluetoothSettingsUiState())
    private fun syncBluetooth() {
        bluetoothController.refresh()
        val value = bluetoothController.state
        bluetooth = BluetoothSettingsUiState(value.enabled, value.paired, value.readFailed)
    }
    private val wifiSession by lazy { WifiJoinSession.get(this) }
    private val wifiSource get() = wifiSession.source
    private val wifiController by lazy { WifiController({ wifiSession.facts() }) { SystemClock.elapsedRealtime() } }
    private var wifiJoin by mutableStateOf<WifiJoinUiState?>(null)
    private var wifiEnableFailed = false
    private fun wifiVisible() = resumed && (shell.state.current == ShellRoute.WifiSettings || shell.state.current is ShellRoute.Wifi)
    private fun forgetWifi() {
        if (!wifiVisible() || wifiJoin != null) return
        if (!wifi.canForget) return
        wifiSource.onOpen()
        wifiSession.forget.forget()
        wifiSession.schedule()
        syncWifi()
    }
    private fun openWifiMessage() {
        if (!wifiVisible() || wifiJoin != null) return
        syncWifi()
        val problem = when (wifi.internetStatus) {
            WifiInternetUiStatus.SignInRequired -> org.plainphone.ui.user.WifiProblem.SignInRequired
            WifiInternetUiStatus.NoInternet -> org.plainphone.ui.user.WifiProblem.NoInternet
            null -> return
        }
        shell.state = shell.navigator!!.openWifiMessage(shell.state, problem).state
    }
    private fun showNearbyWifi() {
        if (!wifiVisible() || wifiSession.forget.pending) return
        wifiSource.onOpen()
        wifiEnableFailed = !wifiSession.location.show()
        wifiSession.schedule()
        syncWifi()
    }
    private fun selectWifi(id: String) {
        if (!wifiVisible() || wifiSession.forget.pending) return
        val choice = wifiSession.joinPort.choices().firstOrNull { it.ssid == id } ?: return
        val current = try { wifiSession.facts() } catch (_: Exception) { return }
        if (current.connected && current.ssid?.removeSurrounding("\"") == id) return
        wifiSession.join.select(choice)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        wifiJoin = WifiJoinUiState(choice.name, when (choice.security) {
            WifiSecurity.Open -> WifiJoinKind.Open
            WifiSecurity.Personal -> WifiJoinKind.Personal
            WifiSecurity.Unsupported -> WifiJoinKind.Unsupported
        })
    }
    private fun leaveWifi() {
        wifiSession.leave()
        wifiJoin = null
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun onWifiJoin(event: WifiJoinEvent) {
        if (!wifiVisible()) return
        val current = wifiJoin ?: return
        when (event) {
            is WifiJoinEvent.Password -> if (!current.joining) wifiJoin = current.copy(password = event.value)
            is WifiJoinEvent.ShowPassword -> if (!current.joining) wifiJoin = current.copy(showPassword = event.value)
            WifiJoinEvent.Cancel -> leaveWifi()
            WifiJoinEvent.Connect -> {
                wifiSession.join.connect(current.password)
                wifiJoin = current.copy(password = "", showPassword = false)
                wifiSession.schedule()
            }
        }
        syncWifi()
    }
    private var wifi by mutableStateOf(WifiSettingsUiState())
    private fun syncWifi() {
        wifiSession.join.tick()
        wifiSession.location.tick()
        val joining = wifiSession.join.state
        wifiJoin = if (joining.choice == null) null else wifiJoin?.copy(joining = joining.joining, failed = joining.failed)
        if (wifiJoin == null) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        wifiSession.forget.refresh()
        wifiController.refresh()
        val value = wifiController.state
        val locationOff = try { !wifiSession.locationPort.enabled() } catch (_: Exception) { false }
        val choices = if (!locationOff && !value.readFailed && value.enabled) wifiSession.joinPort.choices() else emptyList()
        val current = try { wifiSession.facts() } catch (_: Exception) { null }
        val rawCurrent = current?.ssid?.removeSurrounding("\"")
        wifi = WifiSettingsUiState(value.enabled, value.connected, value.network, value.nearby,
            value.readFailed, when (value.internetStatus) {
                WifiInternetStatus.SignInRequired -> WifiInternetUiStatus.SignInRequired
                WifiInternetStatus.NoInternet -> WifiInternetUiStatus.NoInternet
                null -> null
            }, locationOff = locationOff, locationEnableFailed = wifiEnableFailed,
            locationOffFailed = wifiSession.location.offFailed,
            canForget = current?.connected == true && current.validated == true &&
                !value.readFailed && !wifiSession.forget.pending && !wifiSession.forget.failed,
            forgetFailed = wifiSession.forget.failed,
            choices = choices.map { WifiNearbyUi(it.ssid, it.name, value.connected && it.ssid == rawCurrent) })
    }
    private val textSizeController by lazy { TextSizeController(AndroidTextSize(this)) }
    private var textSize by mutableStateOf(TextSizeUiState())
    private fun syncTextSize() {
        val value = textSizeController.state
        val percent = value.scale?.let { BigDecimal(it.toString()).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString() }
        textSize = TextSizeUiState(percent, value.canDecrease, value.canIncrease, value.failed)
    }
    private fun onTextSize(event: TextSizeEvent) {
        when (event) {
            TextSizeEvent.Done -> shell.state = shell.navigator!!.back(shell.state).state
            TextSizeEvent.Smaller -> textSizeController.adjust(false)
            TextSizeEvent.Bigger -> textSizeController.adjust(true)
        }
        syncTextSize()
    }
    private val brightnessController by lazy {
        BrightnessController(AndroidBrightness(this, ComponentName(this, PlainphoneAdminReceiver::class.java)))
    }
    private val ringtoneController by lazy { RingtoneController(AndroidRingtone(this)) }
    /** Voice messages practice (PLA-457): records and plays on this phone only. Never sends. */
    private val voice by lazy {
        VoicePracticeHost(this, ComponentName(this, PlainphoneAdminReceiver::class.java)) {
            val names = shell.phone?.snapshot()?.trustedPeople.orEmpty()
            if (names.size >= 2) getString(org.plainphone.ui.R.string.names_or, names[0], names[1]) else names.firstOrNull().orEmpty()
        }.also { host -> host.onLeave = { shell.state = shell.navigator?.back(shell.state)?.state ?: shell.state } }
    }
    private var ringtone by mutableStateOf(RingtoneUiState())
    private fun syncRingtone() {
        val value = ringtoneController.state
        ringtone = RingtoneUiState(value.choices.map { RingtoneOption(it.id, it.title) },
            value.selectedId, value.currentUnlisted, value.canSave, value.failed)
    }
    private fun onRingtone(event: RingtoneEvent) {
        if (!resumed || shell.state.current != ShellRoute.Ringtone) return
        when (event) {
            RingtoneEvent.Back -> {
                ringtoneController.onLeave()
                shell.state = shell.navigator!!.back(shell.state).state
            }
            RingtoneEvent.Done -> if (ringtoneController.done()) {
                ringtoneController.onLeave()
                shell.state = shell.navigator!!.back(shell.state).state
                syncSound()
            }
            is RingtoneEvent.Select -> ringtoneController.select(event.id)
        }
        syncRingtone()
    }
    private val ringVolume by lazy { VolumeController(AndroidVolume(this, AudioManager.STREAM_RING), 1) }
    private val mediaVolume by lazy { VolumeController(AndroidVolume(this, AudioManager.STREAM_MUSIC), 0) }
    private var sound by mutableStateOf(SoundUiState())
    private fun syncSound() {
        sound = SoundUiState(
            ringVolume.state.reading?.let { VolumeUiState(it.level, maxOf(1, it.min), it.max) },
            mediaVolume.state.reading?.let { VolumeUiState(it.level, it.min, it.max) },
            ringFailed = ringVolume.state.failed, mediaFailed = mediaVolume.state.failed,
            ringAdjustable = ringVolume.state.reading?.adjustable ?: true,
            ringtoneTitle = ringtoneController.currentTitle())
    }
    private fun onSound(event: SoundEvent) {
        fun controller(stream: SoundStream) = if (stream == SoundStream.Ring) ringVolume else mediaVolume
        when (event) {
            SoundEvent.Done, SoundEvent.OpenRingtone -> shell.state = shell.navigator!!.onSound(shell.state, event).state
            is SoundEvent.SetLevel -> controller(event.stream).setLevel(event.level)
            is SoundEvent.Adjust -> controller(event.stream).adjust(event.delta)
        }
        syncSound()
    }
    private var brightness by mutableStateOf(BrightnessUiState(null, false))
    private var battery by mutableStateOf(BatteryUiState())
    private fun syncBrightness() {
        val value = brightnessController.state
        brightness = BrightnessUiState(value.percent, value.automatic, value.failed)
    }
    private fun onBrightness(event: BrightnessEvent) {
        when (event) {
            BrightnessEvent.Done -> shell.state = shell.navigator!!.back(shell.state).state
            is BrightnessEvent.Adjust -> brightnessController.adjust(event.delta)
            is BrightnessEvent.SetPercent -> brightnessController.setPercent(event.percent)
            is BrightnessEvent.SetAutomatic -> brightnessController.setAutomatic(event.enabled)
        }
        syncBrightness()
    }
    private var resumed by mutableStateOf(false)
    private var counts by mutableStateOf(CommunicationCounts())
    private var callsUnavailable by mutableStateOf(false)
    private var messagesUnavailable by mutableStateOf(false)
    private var countsEnabled by mutableStateOf(false)
    private var unsupportedCountApps by mutableStateOf(false)

    private fun syncCounts() {
        counts = communication.controller.counts
        callsUnavailable = communication.controller.availability(CountKind.CALLS) == CountAvailability.UNAVAILABLE
        messagesUnavailable = communication.controller.availability(CountKind.MESSAGES) == CountAvailability.UNAVAILABLE
        countsEnabled = communication.controller.enabled
        unsupportedCountApps = communication.controller.unsupportedApps
    }

    private val runtime by lazy { PolicyRuntime.get(this) }
    private val callingActions by lazy {
        CallingScreenActions(runtime::opened, ::openPhone, ::openEmergency, { runtime.showInCallScreen() }) { action, uri ->
            launch(Intent(action, uri?.let(Uri::parse)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        communication = AndroidCommunicationCounts(this, ::syncCounts)
        syncCounts()
        enterLockTask()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        shell.start(if (debuggable) intent.getStringExtra(EXTRA_PREVIEW_SCREEN) else null)
        // Recreated for a configuration change (text size): the practice comes back where it was (PLA-479).
        voice.restoreOnOpen(shell.voicePlace?.takeIf { shell.state.current == ShellRoute.VoicePractice })
        shell.voicePlace = null
        val device = Preview2Device(applicationContext)
        val mapper = ShellUiMapper(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
        setContent {
            val gate by runtime.gate.collectAsState()
            val refresh by runtime.refresh.collectAsState()
            val runs by runtime.runs.collectAsState()
            val notice by runtime.notice.collectAsState()
            LaunchedEffect(runs) { enterLockTask() }
            val homeVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.Home && cancelScreen == null && !attentionOpen
            val settingsVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.PhoneSettings
            val basicSettingsVisible = resumed && gate == HomeGate.READY && cancelScreen == null && !attentionOpen
            DisposableEffect(basicSettingsVisible, shell.state.current) {
                val source = if (basicSettingsVisible && shell.state.current == ShellRoute.Battery)
                    BatterySource(this@MainActivity) { battery = it } else null
                onDispose { source?.close() }
            }
            DisposableEffect(basicSettingsVisible, shell.state.current) {
                val scanVisible = basicSettingsVisible && shell.state.current == ShellRoute.BluetoothDiscovery
                if (scanVisible) { discoverySource.onOpen(); discoveryController.open() }
                onDispose { if (scanVisible) discoveryController.close() }
            }
            DisposableEffect(basicSettingsVisible, shell.state.current) {
                val chooserVisible = basicSettingsVisible && shell.state.current == ShellRoute.Ringtone
                if (chooserVisible) { ringtoneController.onOpen(); syncRingtone() }
                onDispose { if (chooserVisible) ringtoneController.onLeave() }
            }
            LaunchedEffect(basicSettingsVisible, shell.state.current) {
                if (basicSettingsVisible && shell.state.current == ShellRoute.BluetoothDiscovery) {
                    while (true) { discoveryController.checkAvailable(); delay(1_000) }
                }
                if (basicSettingsVisible && shell.state.current == ShellRoute.Ringtone) {
                    while (true) {
                        ringtoneController.refreshPermission()
                        syncRingtone()
                        delay(1_000)
                    }
                }
                if (basicSettingsVisible && shell.state.current == ShellRoute.BluetoothSettings) {
                    try {
                        bluetoothSource.onOpen()
                        while (true) {
                            syncBluetooth()
                            delay(1_000)
                        }
                    } finally {
                        bluetoothController.clear()
                        bluetooth = BluetoothSettingsUiState()
                    }
                }
                if (basicSettingsVisible && (shell.state.current == ShellRoute.WifiSettings || shell.state.current is ShellRoute.Wifi)) {
                    wifiEnableFailed = false
                    wifiSource.onOpen()
                    wifiController.onOpen()
                    try {
                        while (true) { syncWifi(); delay(1_000) }
                    } finally { leaveWifi() }
                }
                if (basicSettingsVisible && shell.state.current == ShellRoute.TextSize) {
                    textSizeController.onOpen()
                    syncTextSize()
                    while (true) {
                        delay(1_000)
                        textSizeController.refresh()
                        syncTextSize()
                    }
                }
                if (basicSettingsVisible && shell.state.current == ShellRoute.Sound) {
                    ringVolume.onOpen()
                    mediaVolume.onOpen()
                    syncSound()
                    // No public per-stream volume callback. Poll only the visible, resumed page.
                    while (true) {
                        delay(1_000)
                        ringVolume.refresh()
                        mediaVolume.refresh()
                        syncSound()
                    }
                }
                if (basicSettingsVisible && shell.state.current == ShellRoute.Brightness) {
                    brightnessController.refresh()
                    syncBrightness()
                    while (true) {
                        delay(1_000)
                        brightnessController.refresh()
                        syncBrightness()
                    }
                }
            }
            SideEffect {
                communication.controller.setVisible(homeVisible)
                communication.controller.setSettingsVisible(settingsVisible)
            }
            if (gate != HomeGate.READY || !enterLockTask(followUp = false)) {
                val fallback by runtime.fallback.collectAsState()
                PlainphoneTheme {
                    CheckingScreen(onPhone = ::openPhone, onEmergency = ::openEmergency, attention = checkingShowsAttention(gate, notice),
                        details = fallback?.let(::fallbackDetails).orEmpty())
                }
                return@setContent
            }
            // Each ready read-back run refreshes the app list and the attention notice.
            LaunchedEffect(refresh) { shell.prepare(device, attention = refresh.notice != null, productName = getString(R.string.product_name)) }
            // Nothing but paper until the app list is read.
            val phone = shell.phone ?: return@setContent
            LaunchedEffect(phone) {
                Log.i(PolicyRuntime.TAG, "home shown lockTask=${runtime.lockTaskState()} at=${SystemClock.elapsedRealtime()}")
            }
            var snapshot by remember(phone) { mutableStateOf(phone.snapshot()) }
            LaunchedEffect(shell.state, phone) {
                while (true) {
                    snapshot = phone.snapshot()
                    delay(1_000)
                }
            }
            val cancel = cancelScreen
            val screens = HomeScreens(cancel != null, cancel?.state?.sending == true, attentionOpen)
            BackHandler(enabled = screens.handlesBack) { onSystemBack() }
            if (gate == HomeGate.READY && cancel != null) {
                val wait by runtime.waitingNotice.collectAsState()
                LaunchedEffect(wait) { followOperation() }
                PlainphoneTheme {
                    Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                        WaitingChangeScreen(cancel.state) { onCancelEvent(cancel, it) }
                    }
                }
                return@setContent
            }
            if (gate == HomeGate.READY && attentionOpen) {
                val read by runtime.waitingRead.collectAsState()
                PlainphoneTheme {
                    Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                        NeedsAttentionScreen(NeedsAttentionUiState(pendingChange(read,
                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))) { event ->
                            when (event) {
                                NeedsAttentionEvent.Back -> attentionOpen = false
                                NeedsAttentionEvent.CancelChange -> read?.notice?.let { openCancel(it.requestId) }
                            }
                        }
                    }
                }
                return@setContent
            }
            val waitingRead by runtime.waitingRead.collectAsState()
            val settingsWait = settingsWaitingChange(waitingRead)
            PlainphoneShell(shell.state, snapshot, mapper, shell.navigator ?: return@setContent,
                notice = notice, onNotice = ::openNotice,
                counts = counts,
                isDeviceSecure = getSystemService(KeyguardManager::class.java).isDeviceSecure,
                versionName = remember { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() },
                textSize = textSize, onTextSize = ::onTextSize,
                wifi = wifi, wifiJoin = wifiJoin, onWifiJoin = ::onWifiJoin,
                onWifiShow = ::showNearbyWifi, onWifiSelect = ::selectWifi,
                onWifiForget = ::forgetWifi, onWifiMessage = ::openWifiMessage, bluetooth = bluetooth,
                sound = sound, onSound = ::onSound,
                ringtone = ringtone, onRingtone = ::onRingtone,
                discovery = discovery, onDiscovery = ::onDiscovery, onPairNew = ::openDiscovery,
                brightness = brightness, battery = battery, onBrightness = ::onBrightness,
                voice = voice.ui, onVoiceOpen = voice::open, onVoiceClose = { voice.close() }, onVoice = voice::onEvent,
                settings = PhoneSettingsUiState(countsEnabled, callsUnavailable, messagesUnavailable, unsupportedCountApps,
                    pendingChange = settingsWait?.let { getString(waitingChangeTextResource(it.kind), getString(R.string.app_name)) }),
                onSettings = { event ->
                    when (event) {
                        PhoneSettingsEvent.OpenWaitingChange -> settingsWait?.let { openCancel(it.requestId) }
                        PhoneSettingsEvent.OpenWifi -> { wifiSession.forget.onOpen(); shell.state = shell.navigator!!.onSettings(shell.state, event).state }
                        PhoneSettingsEvent.Back, PhoneSettingsEvent.OpenBluetooth, PhoneSettingsEvent.OpenTextSize, PhoneSettingsEvent.OpenBrightness, PhoneSettingsEvent.OpenSound, PhoneSettingsEvent.OpenBattery ->
                            shell.state = shell.navigator!!.onSettings(shell.state, event).state
                        is PhoneSettingsEvent.ShowCommunicationCountsChanged -> {
                            communication.controller.setEnabled(event.enabled)
                            syncCounts()
                        }
                    }
                }) { step ->
                shell.state = step.state
                step.effect?.let(::perform)
            }
        }
    }

    /** The Home key always lands on the home screen, whatever Plainphone screen was open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) { leaveWifi(); shell.state = ShellState() }
        // This opens only the existing PIN-free view; its controller rechecks the current request.
        if (intent.getBooleanExtra("open_waiting_change", false)) openNotice()
        enterLockTask()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        runtime.homeResumed()
        runtime.onUserUnlocked()
        enterLockTask()
    }

    override fun onPause() {
        leaveWifi()
        discoveryController.close()
        ringtoneController.stopPreview()
        resumed = false
        communication.controller.setVisible(false)
        communication.controller.setSettingsVisible(false)
        syncCounts()
        super.onPause()
    }

    /** Visible again: a practice screen that is still showing gets its call and screen-off guards back. */
    override fun onStart() {
        super.onStart()
        voice.onActivityStart()
    }

    /** Not visible any more: a recording stops here and is kept (UX V11). */
    override fun onStop() {
        voice.onActivityStop()
        // Before the composition goes away: on a recreation it closes the route, which forgets the place.
        shell.voicePlace = if (isChangingConfigurations) voice.place() else null
        super.onStop()
    }

    override fun onDestroy() {
        voice.shutdown()
        ringtoneController.onLeave()
        communication.close()
        super.onDestroy()
    }

    /**
     * True when lock task is LOCKED, starting it first only if Keyguard first allows it now. PINNED is
     * someone else's pin, not ours to start over. With [followUp], a LOCKED read reopens the calling
     * screen recovery displaced; never from composition.
     */
    private fun enterLockTask(followUp: Boolean = true): Boolean {
        var state = runtime.lockTaskState()
        if ((state == LockTaskState.NONE || state == LockTaskState.UNREADABLE) && runtime.mayEnterLockTask()) {
            try {
                startLockTask()
            } catch (e: RuntimeException) {
                // Read below: still not LOCKED.
            }
            state = runtime.lockTaskState()
        }
        if (followUp) runtime.onHomeLockTaskState(state)?.let(::reopen)
        return state.contained
    }

    /** The attention screen's lines while fallback containment holds (PLA-344). */
    private fun fallbackDetails(notice: FallbackNotice): List<String> = listOfNotNull(
        getString(R.string.fallback_disclosure),
        notice.others.takeIf { it.isNotEmpty() }?.let { getString(R.string.fallback_others, it.joinToString(", ")) },
        getString(R.string.fallback_incomplete).takeIf { notice.incomplete },
    )

    /** Brings back a displaced calling screen, now inside lock task. Never ends or touches a call. */
    private fun reopen(screen: CallingScreen) {
        Log.i(PolicyRuntime.TAG, "reopening $screen inside lock task at=${SystemClock.elapsedRealtime()}")
        callingActions.reopen(screen)
    }

    /**
     * The notice line's tap: "needs attention" opens its screen (wiring 4); a pending-change line opens the
     * PIN-free cancel (wiring 3).
     */
    private fun openNotice() {
        when (val tap = runtime.noticeTap.value) {
            NoticeTap.OPEN_ATTENTION -> attentionOpen = true
            is NoticeTap.ConfirmCancel -> openCancel(tap.requestId)
            else -> {}
        }
    }

    /** Anyone holding the phone may cancel the pending change [requestId], without a PIN, if it is still pending. */
    private fun openCancel(requestId: String) {
        val wait = runtime.waitingNotice.value?.takeIf { confirmStage(requestId, it) == WaitingStage.ConfirmCancel } ?: return
        val change = getString(waitingChangeTextResource(wait.kind), getString(R.string.app_name))
        cancelScreen = CancelScreen(requestId, WaitingChangeUiState(stage = WaitingStage.ConfirmCancel, change = change,
            turningOff = wait.kind == WaitingChangeKind.TURN_OFF))
    }

    /**
     * Yes: cancel, then show what was written ([cancelStage]): "Cancelled" only after the cancelled state
     * is saved. Any other choice, or OK afterwards, goes back to Home.
     */
    private fun onCancelEvent(screen: CancelScreen, event: WaitingChangeEvent) {
        if (event != WaitingChangeEvent.ConfirmCancel || screen.state.stage != WaitingStage.ConfirmCancel) {
            cancelScreen = null
            return
        }
        if (screen.state.sending) return
        cancelScreen = screen.copy(state = screen.state.copy(sending = true))
        runtime.cancelWait(screen.requestId) { result ->
            runOnUiThread {
                cancelScreen = cancelStage(result)?.let { screen.copy(state = screen.state.copy(stage = it, sending = false)) }
            }
        }
    }

    /**
     * While "Cancel this change?" waits for a choice, it tracks the current operation: committing shows
     * "Already in progress" with no Cancel, and a finished or replaced request closes it. Once Yes is
     * pressed, the cancel's own result decides.
     */
    private fun followOperation() {
        val screen = cancelScreen ?: return
        if (screen.state.stage != WaitingStage.ConfirmCancel || screen.state.sending) return
        val stage = confirmStage(screen.requestId, runtime.waitingNotice.value)
        cancelScreen = when (stage) {
            null -> null
            WaitingStage.ConfirmCancel -> screen
            else -> screen.copy(state = screen.state.copy(stage = stage))
        }
    }

    /** Android Back on a sub-screen ([HomeScreens.back]); read fresh, since the handler outlives composition. */
    private fun onSystemBack() {
        val cancel = cancelScreen
        val after = HomeScreens(cancel != null, cancel?.state?.sending == true, attentionOpen).back()
        if (!after.cancelOpen) cancelScreen = null
        attentionOpen = after.attentionOpen
    }

    private data class CancelScreen(val requestId: String, val state: WaitingChangeUiState)
    private var cancelScreen by mutableStateOf<CancelScreen?>(null)
    private var attentionOpen by mutableStateOf(false)

    private fun openPhone() {
        runtime.opened(CallingScreen.PHONE)
        launch(BuildControl.control.phoneIntent(Intent(Intent.ACTION_DIAL)))
    }

    private fun openEmergency() {
        runtime.opened(CallingScreen.EMERGENCY_KEYPAD)
        openEmergencyDialer { action, uri ->
            launch(Intent(action, uri?.let(Uri::parse)))
        }
    }

    private fun perform(effect: ShellEffect) {
        if (callingActions.perform(effect)) return
        val intent = when (effect) {
            ShellEffect.OpenDialer, ShellEffect.OpenEmergencyDialer -> null // Handled above.
            ShellEffect.OpenMessages ->
                CommunicationEntryIntents.messages()
            ShellEffect.OpenTrustedPerson -> Intent(this, PinAccessActivity::class.java)
            is ShellEffect.OpenApp -> packageManager.getLaunchIntentForPackage(effect.id)
            ShellEffect.Finish -> null.also { finish() }
        } ?: return
        launch(intent)
    }

    private fun launch(intent: Intent): Boolean {
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(PolicyRuntime.TAG, "no activity for ${intent.action}")
            false
        }
    }

    companion object {
        /** Debug builds only: `more`, `blocked_link`, `wifi_sign_in` or `wifi_no_internet`. */
        const val EXTRA_PREVIEW_SCREEN = "org.plainphone.preview.SCREEN"
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
    }
}

/** Keeps the back stack and the phone across configuration changes such as text size. */
class ShellViewModel : ViewModel() {
    /** Null until [prepare] has read the app list after a read-back run. */
    var phone by mutableStateOf<PreviewPhone?>(null)
        private set
    var navigator: ShellNavigator? = null
        private set
    var state by mutableStateOf(ShellState())
    /** The practice screen's place while MainActivity is recreated; ids and enums only, never audio. */
    var voicePlace: VoicePlace? = null
    private var started = false
    private var prepared = 0

    /**
     * Reads the starter apps off the main thread, after the runtime has re-applied and read back the
     * policy, then shows Home with the real app list. The latest call wins.
     */
    fun prepare(device: Preview2Device, attention: Boolean, productName: String) {
        val generation = ++prepared
        thread(name = "preview2-home") {
            val apps = runCatching { device.homeApps() }.getOrDefault(emptyList()).map { (id, label) -> ApprovedApp(id, label) }
            val sample = PreviewPhone.sample()
            val ready = PreviewPhone(sample.copy(apps = apps, notices = NoticeInput(sample.mode, needsAttention = attention)))
            Handler(Looper.getMainLooper()).post {
                if (generation != prepared) return@post
                navigator = ShellNavigator(ready, ready, productName, testBuild = true)
                phone = ready
            }
        }
    }

    fun start(entry: String?) {
        if (started) return
        started = true
        state = ShellState.forPreviewEntry(entry)
    }
}
