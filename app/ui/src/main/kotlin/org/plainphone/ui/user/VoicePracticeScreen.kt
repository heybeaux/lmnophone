/* Copyright 2026 LMNOPhone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.plainphone.ui.R
import org.plainphone.ui.components.*
import org.plainphone.ui.theme.*

/**
 * Voice messages. Slice 1 (PLA-457) is a practice: nothing is sent, and every practice state says so.
 * Slice 2a (PLA-499) adds Send (V4-V8) for someone who can be sent to, when real sending is on; those
 * screens drop the practice band. "Sent" means only that the phone network accepted the message.
 * This flow alone may use pictures and symbols for someone who cannot read: the person's picture
 * is a monochrome initial for now, and record/stop/listen symbols always come with their word.
 */
data class VoicePersonUi(val id: String, val name: String, val pictureInInk: Boolean = false)
enum class VoiceUiNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay, Busy, Unsent }
enum class VoiceUiMicProblem { NotAllowed, TurnedOff, DidNotWork }
/** V7 (the network or phone said no), V8 (no phone signal), or no answer known after a restart. */
enum class VoiceUiSendProblem { NoSignal, NotSent, NotConfirmed }

sealed interface VoiceUiState {
    /** True: show "Practice – not sent". False only where this screen can really send. */
    val practice: Boolean

    data class ChoosePerson(val people: List<VoicePersonUi>, val fromHome: Boolean = false,
        override val practice: Boolean = true) : VoiceUiState
    data class Person(val person: VoicePersonUi, val notice: VoiceUiNotice? = null, val openMessages: Boolean = false,
        val messagesUnavailable: Boolean = false, override val practice: Boolean = true) : VoiceUiState
    data class Recording(val person: VoicePersonUi, override val practice: Boolean = true) : VoiceUiState
    /** [canSend]: V4 with the black Send button. Without it, the practice review (V13). */
    data class Review(val person: VoicePersonUi, val playing: Boolean = false, val notice: VoiceUiNotice? = null,
        val openMessages: Boolean = false, val messagesUnavailable: Boolean = false,
        val canSend: Boolean = false, override val practice: Boolean = true) : VoiceUiState
    data class MicOff(val person: VoicePersonUi, val problem: VoiceUiMicProblem,
        override val practice: Boolean = true) : VoiceUiState
    /** V5: nothing to tap but Back; leaving doesn't stop the send. */
    data class Sending(val person: VoicePersonUi) : VoiceUiState { override val practice get() = false }
    /** V6. */
    data class Sent(val person: VoicePersonUi) : VoiceUiState { override val practice get() = false }
    /** V7, V8 or not confirmed. [helper]: the trusted person, named only after a second failure (V7 again). */
    data class NotSent(val person: VoicePersonUi, val problem: VoiceUiSendProblem, val helper: String? = null) : VoiceUiState {
        override val practice get() = false
    }
}

/** Send and Try again are taps only: nothing here, or behind it, sends by itself. */
sealed interface VoiceUiEvent {
    data class Choose(val id: String) : VoiceUiEvent
    data object Record : VoiceUiEvent
    data object Stop : VoiceUiEvent
    data object Play : VoiceUiEvent
    data object RecordAgain : VoiceUiEvent
    data object Delete : VoiceUiEvent
    data object Back : VoiceUiEvent
    data object HearName : VoiceUiEvent
    data object OpenMessages : VoiceUiEvent
    data object Send : VoiceUiEvent
    data object TryAgain : VoiceUiEvent
    data object Done : VoiceUiEvent
}

private enum class Symbol { Record, Stop, Play, Cross, Send }

/**
 * The header and notices scroll; the actions (Record, Stop, Try again, and on review Record again, Delete
 * and Listen) are pinned below them so they stay whole on screen at any text size and name length
 * (PLA-465 finding 3, PLA-473). While recording, the "Recording" line is pinned with Stop, so the
 * visible recording state never scrolls away.
 */
@Composable
fun VoicePracticeScreen(state: VoiceUiState, onEvent: (VoiceUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = PlainphoneSpace.S6).padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S4),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!(state is VoiceUiState.Person && state.openMessages)) ScreenLink(stringResource(if (state is VoiceUiState.ChoosePerson) { if (state.fromHome) R.string.family_back else R.string.back_more } else R.string.voice_back_people),
                { onEvent(VoiceUiEvent.Back) }, centered = false)
            if (state.practice) Text(stringResource(R.string.voice_practice_label), style = PlainphoneType.Button.copy(color = PlainphoneColors.Ink2),
                modifier = Modifier.fillMaxWidth().background(PlainphoneColors.Fill1, RoundedCornerShape(PlainphoneSpace.BoxRadius))
                    .padding(horizontal = PlainphoneSpace.S4, vertical = PlainphoneSpace.S2), textAlign = TextAlign.Center)
            when (state) {
                is VoiceUiState.ChoosePerson -> {
                    Text(stringResource(R.string.voice_choose), style = PlainphoneType.Title,
                        modifier = Modifier.fillMaxWidth().padding(vertical = PlainphoneSpace.S4).semantics { heading() })
                    if (state.people.isEmpty()) Text(stringResource(R.string.voice_empty_family), style = PlainphoneType.Body)
                    for (p in state.people) PersonRow(p, state.fromHome) { onEvent(VoiceUiEvent.Choose(p.id)) }
                }
                is VoiceUiState.Person -> {
                    PersonHeader(state.person, onEvent, showHearLink = !state.openMessages)
                    Notice(state.notice, state.person)
                    if (state.messagesUnavailable) Text(stringResource(R.string.family_messages_unavailable), style = PlainphoneType.Body)
                    if (state.openMessages) MessagesButton(state.person, onEvent)
                }
                is VoiceUiState.Recording -> PersonHeader(state.person, onEvent, hearName = false)
                is VoiceUiState.Review -> {
                    PersonHeader(state.person, onEvent)
                    Notice(state.notice, state.person)
                    if (state.messagesUnavailable) Text(stringResource(R.string.family_messages_unavailable), style = PlainphoneType.Body)
                    if (state.openMessages) MessagesButton(state.person, onEvent)
                }
                is VoiceUiState.MicOff -> {
                    PersonHeader(state.person, onEvent)
                    Text(stringResource(when (state.problem) {
                        VoiceUiMicProblem.NotAllowed -> R.string.voice_mic_not_allowed
                        VoiceUiMicProblem.TurnedOff -> R.string.voice_mic_off
                        VoiceUiMicProblem.DidNotWork -> R.string.voice_mic_error
                    }), style = PlainphoneType.Body, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = PlainphoneSpace.S4).semantics { liveRegion = LiveRegionMode.Polite })
                }
                // The outcome title names the person, so it takes the name's place under the picture and stays
                // on screen at any text size; the picture still says the name when tapped.
                is VoiceUiState.Sending -> {
                    PersonHeader(state.person, onEvent, title = false)
                    SendTitle(stringResource(R.string.voice_sending, state.person.name))
                }
                is VoiceUiState.Sent -> {
                    PersonHeader(state.person, onEvent, title = false)
                    SendTitle(stringResource(R.string.voice_sent, state.person.name))
                }
                is VoiceUiState.NotSent -> {
                    PersonHeader(state.person, onEvent, title = state.problem != VoiceUiSendProblem.NotSent)
                    when (state.problem) {
                        VoiceUiSendProblem.NotSent -> {
                            SendTitle(stringResource(R.string.voice_not_sent, state.person.name))
                            SendBody(stringResource(R.string.voice_still_here))
                            state.helper?.let { SendBody(stringResource(R.string.voice_not_sent_again, it)) }
                        }
                        VoiceUiSendProblem.NoSignal -> {
                            SendTitle(stringResource(R.string.voice_no_signal))
                            SendBody(stringResource(R.string.voice_no_signal_body))
                        }
                        // No answer known after a restart (N1): neither sent nor not sent, so it says it may not be.
                        VoiceUiSendProblem.NotConfirmed -> {
                            SendTitle(stringResource(R.string.voice_not_confirmed))
                            SendBody(stringResource(R.string.voice_not_confirmed_body, state.person.name))
                        }
                    }
                }
            }
        }
        if (state is VoiceUiState.ChoosePerson || state is VoiceUiState.Sending) return@Column
        Spacer(Modifier.fillMaxWidth().height(PlainphoneSpace.Border).background(PlainphoneColors.Fill2))
        Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2),
            horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                is VoiceUiState.ChoosePerson -> Unit
                is VoiceUiState.Person -> {
                    if (state.openMessages) ScreenLink(stringResource(R.string.family_back), { onEvent(VoiceUiEvent.Back) })
                    BigButton(stringResource(R.string.voice_record), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
                }
                is VoiceUiState.Recording -> {
                    Row(Modifier.padding(bottom = PlainphoneSpace.S2).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                        SymbolMark(Symbol.Record, 20.dp, PlainphoneColors.Ink)
                        Text(stringResource(if (state.practice) R.string.voice_recording_practice else R.string.voice_recording),
                            style = PlainphoneType.Row)
                    }
                    BigButton(stringResource(R.string.voice_stop), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                }
                is VoiceUiState.Review -> if (state.canSend) {
                    // UX V4 with Send: three outline buttons 16 dp apart, then 32 dp before the black Send.
                    Spacer(Modifier.height(PlainphoneSpace.S2))
                    if (state.playing) SmallButton(stringResource(R.string.voice_stop_listening), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                    else SmallButton(stringResource(R.string.voice_listen), Symbol.Play) { onEvent(VoiceUiEvent.Play) }
                    Spacer(Modifier.height(PlainphoneSpace.S4))
                    SmallButton(stringResource(R.string.voice_record_again), Symbol.Record) { onEvent(VoiceUiEvent.RecordAgain) }
                    Spacer(Modifier.height(PlainphoneSpace.S4))
                    SmallButton(stringResource(R.string.voice_delete), Symbol.Cross) { onEvent(VoiceUiEvent.Delete) }
                    Spacer(Modifier.height(32.dp))
                    BigButton(stringResource(R.string.voice_send_to, state.person.name), Symbol.Send) { onEvent(VoiceUiEvent.Send) }
                } else {
                    // UX V13: outline buttons stacked with 16 dp between, then 32 dp before the black button.
                    Spacer(Modifier.height(PlainphoneSpace.S2))
                    SmallButton(stringResource(R.string.voice_record_again), Symbol.Record) { onEvent(VoiceUiEvent.RecordAgain) }
                    Spacer(Modifier.height(PlainphoneSpace.S4))
                    SmallButton(stringResource(R.string.voice_delete), Symbol.Cross) { onEvent(VoiceUiEvent.Delete) }
                    Spacer(Modifier.height(32.dp))
                    if (state.playing) BigButton(stringResource(R.string.voice_stop_listening), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                    else BigButton(stringResource(R.string.voice_listen), Symbol.Play) { onEvent(VoiceUiEvent.Play) }
                }
                is VoiceUiState.MicOff -> BigButton(stringResource(R.string.voice_try_again), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
                is VoiceUiState.Sending -> Unit
                is VoiceUiState.Sent -> BigButton(stringResource(R.string.voice_done), null) { onEvent(VoiceUiEvent.Done) }
                is VoiceUiState.NotSent -> {
                    // V7: Listen above Try again. V8 has Try again only.
                    if (state.problem != VoiceUiSendProblem.NoSignal) {
                        Spacer(Modifier.height(PlainphoneSpace.S2))
                        SmallButton(stringResource(R.string.voice_listen), Symbol.Play) { onEvent(VoiceUiEvent.Play) }
                        Spacer(Modifier.height(32.dp))
                    }
                    BigButton(stringResource(R.string.voice_try_again), Symbol.Send) { onEvent(VoiceUiEvent.TryAgain) }
                }
            }
        }
    }
}

@Composable
private fun SendTitle(text: String) {
    Text(text, style = PlainphoneType.Title.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S2)
            .semantics { heading(); liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun SendBody(text: String) {
    Text(text, style = PlainphoneType.Body.copy(hyphens = Hyphens.Auto), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S4).semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun PersonRow(person: VoicePersonUi, family: Boolean = false, onClick: () -> Unit) {
    if (family) {
        Column(Modifier.fillMaxWidth().plainphoneAction(onClick = onClick).padding(vertical = PlainphoneSpace.S4),
            horizontalAlignment = Alignment.CenterHorizontally) {
            PersonPicture(person, 120.dp, decorative = true)
            Text(person.name, style = PlainphoneType.App, textAlign = TextAlign.Center)
        }
        return
    }
    Row(Modifier.fillMaxWidth().plainphoneAction(onClick = onClick).heightIn(min = 96.dp)
        .padding(vertical = PlainphoneSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        PersonPicture(person, 72.dp, decorative = true)
        Text(person.name, style = PlainphoneType.App.copy(hyphens = Hyphens.Auto), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PersonHeader(person: VoicePersonUi, onEvent: (VoiceUiEvent) -> Unit, hearName: Boolean = true,
    showHearLink: Boolean = true, title: Boolean = true) {
    val hear = { onEvent(VoiceUiEvent.HearName) }
    Spacer(Modifier.height(PlainphoneSpace.S4))
    PersonPicture(person, if (person.pictureInInk && hearName) 120.dp else 96.dp, onHearName = if (hearName) hear else null)
    if (!title) return
    // Never cut or shrunk (README, screen titles): a word too long for the line breaks with a hyphen.
    Text(person.name, style = PlainphoneType.Title.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S2).semantics { heading() })
    // The picture carries this action for TalkBack, so the link is hidden there and not offered twice (UX V2).
    if (hearName && showHearLink) Box(Modifier.clearAndSetSemantics {}) { ScreenLink(stringResource(R.string.voice_hear_name), hear) }
    else if (!hearName) Spacer(Modifier.height(48.dp))
}

/**
 * Placeholder picture: the person's initial in a monochrome circle. With [onHearName], tapping it says
 * the name again (UX V2 picture tap); TalkBack reads "Picture of Sam" and offers "hear the name".
 */
@Composable
private fun PersonPicture(person: VoicePersonUi, size: Dp, decorative: Boolean = false, onHearName: (() -> Unit)? = null) {
    val description = stringResource(R.string.voice_picture, person.name)
    val actionLabel = stringResource(R.string.voice_hear_name_action)
    // The outer box owns the semantics; it clears the inner tap target's, so TalkBack gets one labelled action.
    Box(Modifier.size(size).then(if (decorative) Modifier.clearAndSetSemantics {} else Modifier.clearAndSetSemantics {
        contentDescription = description
        if (onHearName != null) onClick(label = actionLabel) { onHearName(); true }
    })) { Box(Modifier.fillMaxSize()
        .then(if (onHearName != null) Modifier.plainphoneAction(shape = CircleShape, onClick = onHearName) else Modifier)
        .background(if (person.pictureInInk) PlainphoneColors.Ink else PlainphoneColors.Fill2, CircleShape)
        .border(PlainphoneSpace.Border, PlainphoneColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
        // Sized in dp, not scaled with the text size: it is a picture.
        val letter = with(LocalDensity.current) { (size * 0.5f).toSp() }
        Text(initial(person.name), style = PlainphoneType.Title.copy(fontSize = letter, lineHeight = letter, color = if (person.pictureInInk) PlainphoneColors.Paper else PlainphoneColors.Ink), maxLines = 1)
    } }
}

private fun initial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

@Composable
private fun Notice(notice: VoiceUiNotice?, person: VoicePersonUi) {
    if (notice == null) { Spacer(Modifier.height(PlainphoneSpace.S4)); return }
    Text(if (notice == VoiceUiNotice.Unsent) stringResource(R.string.voice_notice_unsent, person.name) else stringResource(when (notice) {
        VoiceUiNotice.MaxLength -> R.string.voice_notice_max
        VoiceUiNotice.Interrupted -> R.string.voice_notice_interrupted
        VoiceUiNotice.NothingRecorded -> R.string.voice_notice_nothing
        VoiceUiNotice.Deleted -> R.string.voice_notice_deleted
        VoiceUiNotice.CouldNotPlay -> R.string.voice_notice_play
        VoiceUiNotice.Busy -> R.string.voice_notice_busy
        VoiceUiNotice.Unsent -> error("formatted above")
    }), style = PlainphoneType.Body, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = PlainphoneSpace.S4).semantics { liveRegion = LiveRegionMode.Polite })
}

/**
 * At least 96 dp each way: a large symbol beside its word, inside one full-width target. Without a symbol
 * (Done, V6) it is a plain black button.
 */
@Composable
private fun BigButton(word: String, symbol: Symbol?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(PlainphoneSpace.BoxRadius)
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp)
        .then(if (symbol == null) Modifier.background(PlainphoneColors.Ink, shape) else Modifier)
        .plainphoneAction(shape = RoundedCornerShape(PlainphoneSpace.BoxRadius), onClick = onClick)
        .padding(horizontal = PlainphoneSpace.S3, vertical = PlainphoneSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4, Alignment.CenterHorizontally)) {
        if (symbol != null) Box(Modifier.size(80.dp).background(PlainphoneColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
            SymbolMark(symbol, 32.dp, PlainphoneColors.Paper)
        }
        Text(word, style = PlainphoneType.Row.copy(hyphens = Hyphens.Auto,
            color = if (symbol == null) PlainphoneColors.Paper else PlainphoneColors.Ink), modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun SmallButton(word: String, symbol: Symbol?, description: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
        .semantics { if (description != null) contentDescription = description }
        .border(PlainphoneSpace.Border, PlainphoneColors.Ink, RoundedCornerShape(PlainphoneSpace.BoxRadius))
        .plainphoneAction(shape = RoundedCornerShape(PlainphoneSpace.BoxRadius), onClick = onClick)
        .padding(horizontal = PlainphoneSpace.S4, vertical = PlainphoneSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3, Alignment.CenterHorizontally)) {
        if (symbol != null) SymbolMark(symbol, 20.dp, PlainphoneColors.Ink)
        Text(word, style = PlainphoneType.Button, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MessagesButton(person: VoicePersonUi, onEvent: (VoiceUiEvent) -> Unit) {
    SmallButton(stringResource(R.string.family_messages, person.name), null,
        stringResource(R.string.family_messages_desc, person.name)) { onEvent(VoiceUiEvent.OpenMessages) }
    Spacer(Modifier.height(PlainphoneSpace.S6))
}

/** Monochrome symbols, no semantics: the paired word is the accessible name. */
@Composable
private fun SymbolMark(symbol: Symbol, size: Dp, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(size).clearAndSetSemantics {}) {
        when (symbol) {
            Symbol.Record -> drawCircle(color)
            Symbol.Stop -> drawRect(color, topLeft = Offset(this.size.width * 0.1f, this.size.height * 0.1f),
                size = Size(this.size.width * 0.8f, this.size.height * 0.8f))
            Symbol.Cross -> {
                val w = this.size.width * 0.14f
                drawLine(color, Offset(this.size.width * 0.15f, this.size.height * 0.15f),
                    Offset(this.size.width * 0.85f, this.size.height * 0.85f), w)
                drawLine(color, Offset(this.size.width * 0.85f, this.size.height * 0.15f),
                    Offset(this.size.width * 0.15f, this.size.height * 0.85f), w)
            }
            Symbol.Send -> {
                val w = this.size.width
                val h = this.size.height
                drawLine(color, Offset(w * 0.08f, h / 2), Offset(w * 0.62f, h / 2), w * 0.16f)
                drawPath(Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w, h / 2)
                    lineTo(w * 0.5f, h * 0.88f)
                    close()
                }, color)
            }
            Symbol.Play -> drawPath(Path().apply {
                moveTo(this@Canvas.size.width * 0.2f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
                lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height)
                close()
            }, color)
        }
    }
}

@Composable
fun FamilyLetterPicture(name: String) = PersonPicture(VoicePersonUi("", name, pictureInInk = true), 120.dp, decorative = true)
