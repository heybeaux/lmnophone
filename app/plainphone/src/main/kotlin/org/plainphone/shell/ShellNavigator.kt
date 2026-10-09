// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.user.AppRequestResult
import org.plainphone.ui.user.AppRequestResultEvent
import org.plainphone.ui.user.AskForAppEvent
import org.plainphone.ui.user.BlockedLinkEvent
import org.plainphone.ui.user.BlockedLinkUiState
import org.plainphone.ui.user.MoreEvent
import org.plainphone.ui.user.WifiMessageEvent
import org.plainphone.ui.user.WifiProblem

/** The screens the shell can show. */
sealed interface ShellRoute {
    data object Home : ShellRoute
    data object More : ShellRoute
    data object AskForApp : ShellRoute
    data class RequestResult(val result: AppRequestResult) : ShellRoute
    /**
     * [sanitizedTarget] is `{host}{path}` with userinfo, query and fragment already removed.
     * [first] is the stage "Don't send" returns to.
     */
    data class BlockedLink(
        val stage: BlockedLinkUiState,
        val sanitizedTarget: String,
        val first: BlockedLinkUiState = stage,
    ) : ShellRoute
    data class Wifi(val problem: WifiProblem) : ShellRoute
}

/** Back stack (Home at the bottom) and the request being written. */
data class ShellState(val stack: List<ShellRoute> = listOf(ShellRoute.Home), val draft: String = "") {
    val current: ShellRoute get() = stack.last()

    companion object {
        /**
         * Preview-only entry, because blocked links and Wi-Fi messages are opened from outside
         * Plainphone (a tapped link, a Wi-Fi network) and that wiring is a later D1 slice.
         */
        fun forPreviewEntry(name: String?): ShellState {
            val route = when (name) {
                "more" -> ShellRoute.More
                "blocked_link" -> ShellRoute.BlockedLink(
                    BlockedLinkUiState.Blocked("example.invalid", shareWith = "Sam"), "example.invalid/bus")
                "wifi_sign_in" -> ShellRoute.Wifi(WifiProblem.SignInRequired)
                "wifi_no_internet" -> ShellRoute.Wifi(WifiProblem.NoInternet)
                else -> return ShellState()
            }
            return ShellState(listOf(ShellRoute.Home, route))
        }
    }
}

/** Things only the Android host can do. Each is an ordinary app intent, never a policy change. */
sealed interface ShellEffect {
    data object OpenDialer : ShellEffect
    data object OpenMessages : ShellEffect
    /** The system dialer with the emergency number filled in. Never places the call. */
    data object OpenEmergencyDialer : ShellEffect
    data class OpenApp(val id: String) : ShellEffect
    data object Finish : ShellEffect
}

data class Step(val state: ShellState, val effect: ShellEffect? = null)

/** Pure navigation. Destinations whose screens haven't merged yet leave the state unchanged. */
class ShellNavigator(private val phone: PhoneSource, private val outbox: RequestOutbox) {

    fun back(s: ShellState): Step =
        if (s.stack.size > 1) Step(s.copy(stack = s.stack.dropLast(1))) else Step(s, ShellEffect.Finish)

    fun onHome(s: ShellState, e: HomeEvent): Step = when (e) {
        HomeEvent.OpenMore -> push(s, ShellRoute.More)
        HomeEvent.OpenPhone -> Step(s, ShellEffect.OpenDialer)
        HomeEvent.OpenMessages -> Step(s, ShellEffect.OpenMessages)
        is HomeEvent.OpenApp -> Step(s, ShellEffect.OpenApp(e.id))
        HomeEvent.OpenNotice -> Step(s)
    }

    fun onMore(s: ShellState, e: MoreEvent): Step = when (e) {
        MoreEvent.Back -> back(s)
        MoreEvent.OpenEmergencyDialer -> Step(s, ShellEffect.OpenEmergencyDialer)
        MoreEvent.AskForApp -> push(s.copy(draft = ""), ShellRoute.AskForApp)
        MoreEvent.OpenPhoneSettings, MoreEvent.AddApp, MoreEvent.OpenTrustedPerson,
        MoreEvent.OpenSelfManagedSettings, MoreEvent.OpenAbout -> Step(s)
    }

    fun onAsk(s: ShellState, e: AskForAppEvent): Step = when (e) {
        AskForAppEvent.Back -> back(s)
        is AskForAppEvent.MessageChanged -> Step(if (RequestRules.accepts(e.value)) s.copy(draft = e.value) else s)
        AskForAppEvent.Send -> send(s)
        is AskForAppEvent.Retry -> Step(s)
    }

    fun onResult(s: ShellState, e: AppRequestResultEvent): Step = when (e) {
        AppRequestResultEvent.Ok -> back(s)
        AppRequestResultEvent.Retry -> send(back(s).state)
        is AppRequestResultEvent.RetryRequest -> Step(s)
    }

    fun onLink(s: ShellState, e: BlockedLinkEvent): Step {
        val link = s.current as? ShellRoute.BlockedLink ?: return Step(s)
        val snapshot = phone.snapshot()
        fun stage(next: BlockedLinkUiState) = Step(s.copy(stack = s.stack.dropLast(1) + link.copy(stage = next)))
        return when (e) {
            BlockedLinkEvent.GoBack, BlockedLinkEvent.Ok -> back(s)
            BlockedLinkEvent.ReviewSharing -> stage(BlockedLinkUiState.Confirm(snapshot.requestRecipient,
                ShareLinkText.format(snapshot.userName, link.sanitizedTarget)))
            BlockedLinkEvent.DontSend -> stage(link.first)
            // Preview: nothing is sent and the link isn't kept.
            BlockedLinkEvent.Send -> stage(BlockedLinkUiState.Sent(snapshot.requestRecipient))
        }
    }

    fun onWifi(s: ShellState, e: WifiMessageEvent): Step = when (e) {
        WifiMessageEvent.ForgetNetwork, WifiMessageEvent.Ok -> back(s)
    }

    private fun push(s: ShellState, route: ShellRoute) = Step(s.copy(stack = s.stack + route))

    private fun send(s: ShellState): Step {
        if (s.current != ShellRoute.AskForApp || !RequestRules.canSend(s.draft)) return Step(s)
        val result = outbox.send(s.draft)
        val next = if (result == AppRequestResult.Sent) s.copy(draft = "") else s
        return push(next, ShellRoute.RequestResult(result))
    }
}

/** The text a trusted person gets for a shared link (docs/ux/README.md, "Texts Plainphone sends"). */
object ShareLinkText {
    fun format(user: String, sanitizedTarget: String) =
        "Plainphone: $user would like to look at this link with you: $sanitizedTarget"
}
