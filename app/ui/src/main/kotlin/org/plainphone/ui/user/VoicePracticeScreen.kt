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
 * Voice messages, slice 1 (PLA-457): a practice. Nothing is sent, and every state says so.
 * This flow alone may use pictures and symbols for someone who cannot read: the person's picture
 * is a monochrome initial for now, and record/stop/listen symbols always come with their word.
 */
data class VoicePersonUi(val id: String, val name: String, val pictureInInk: Boolean = false)
enum class VoiceUiNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay, Busy, Unsent }
enum class VoiceUiMicProblem { NotAllowed, TurnedOff, DidNotWork }

sealed interface VoiceUiState {
    data class ChoosePerson(val people: List<VoicePersonUi>, val fromHome: Boolean = false) : VoiceUiState
    data class Person(val person: VoicePersonUi, val notice: VoiceUiNotice? = null, val openMessages: Boolean = false, val messagesUnavailable: Boolean = false) : VoiceUiState
    data class Recording(val person: VoicePersonUi) : VoiceUiState
    data class Review(val person: VoicePersonUi, val playing: Boolean = false, val notice: VoiceUiNotice? = null, val openMessages: Boolean = false, val messagesUnavailable: Boolean = false) : VoiceUiState
    data class MicOff(val person: VoicePersonUi, val problem: VoiceUiMicProblem) : VoiceUiState
}

/** Deliberately no send event. */
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
}

private enum class Symbol { Record, Stop, Play, Cross }

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
            ScreenLink(stringResource(if (state is VoiceUiState.ChoosePerson) { if (state.fromHome) R.string.family_back else R.string.back_more } else R.string.voice_back_people),
                { onEvent(VoiceUiEvent.Back) }, centered = false)
            Text(stringResource(R.string.voice_practice_label), style = PlainphoneType.Button.copy(color = PlainphoneColors.Ink2),
                modifier = Modifier.fillMaxWidth().background(PlainphoneColors.Fill1, RoundedCornerShape(PlainphoneSpace.BoxRadius))
                    .padding(horizontal = PlainphoneSpace.S4, vertical = PlainphoneSpace.S2), textAlign = TextAlign.Center)
            when (state) {
                is VoiceUiState.ChoosePerson -> {
                    Text(stringResource(R.string.voice_choose), style = PlainphoneType.Title,
                        modifier = Modifier.fillMaxWidth().padding(vertical = PlainphoneSpace.S4).semantics { heading() })
                    for (p in state.people) PersonRow(p, state.fromHome) { onEvent(VoiceUiEvent.Choose(p.id)) }
                }
                is VoiceUiState.Person -> {
                    PersonHeader(state.person, onEvent)
                    Notice(state.notice, state.person)
                    if (state.messagesUnavailable) Text(stringResource(R.string.family_messages_unavailable), style = PlainphoneType.Body)
                    if (state.openMessages) SmallButton(stringResource(R.string.family_messages), null) { onEvent(VoiceUiEvent.OpenMessages) }
                }
                is VoiceUiState.Recording -> PersonHeader(state.person, onEvent, hearName = false)
                is VoiceUiState.Review -> {
                    PersonHeader(state.person, onEvent)
                    Notice(state.notice, state.person)
                    if (state.messagesUnavailable) Text(stringResource(R.string.family_messages_unavailable), style = PlainphoneType.Body)
                    if (state.openMessages) SmallButton(stringResource(R.string.family_messages), null) { onEvent(VoiceUiEvent.OpenMessages) }
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
            }
        }
        if (state is VoiceUiState.ChoosePerson) return@Column
        Spacer(Modifier.fillMaxWidth().height(PlainphoneSpace.Border).background(PlainphoneColors.Fill2))
        Column(Modifier.fillMaxWidth().padding(horizontal = PlainphoneSpace.S6, vertical = PlainphoneSpace.S2),
            horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                is VoiceUiState.ChoosePerson -> Unit
                is VoiceUiState.Person -> BigButton(stringResource(R.string.voice_record), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
                is VoiceUiState.Recording -> {
                    Row(Modifier.padding(bottom = PlainphoneSpace.S2).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                        SymbolMark(Symbol.Record, 20.dp, PlainphoneColors.Ink)
                        Text(stringResource(R.string.voice_recording_practice), style = PlainphoneType.Row)
                    }
                    BigButton(stringResource(R.string.voice_stop), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                }
                is VoiceUiState.Review -> {
                    // UX V4: outline buttons stacked with 16 dp between, then 32 dp before the black button.
                    Spacer(Modifier.height(PlainphoneSpace.S2))
                    SmallButton(stringResource(R.string.voice_record_again), Symbol.Record) { onEvent(VoiceUiEvent.RecordAgain) }
                    Spacer(Modifier.height(PlainphoneSpace.S4))
                    SmallButton(stringResource(R.string.voice_delete), Symbol.Cross) { onEvent(VoiceUiEvent.Delete) }
                    Spacer(Modifier.height(32.dp))
                    if (state.playing) BigButton(stringResource(R.string.voice_stop_listening), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                    else BigButton(stringResource(R.string.voice_listen), Symbol.Play) { onEvent(VoiceUiEvent.Play) }
                }
                is VoiceUiState.MicOff -> BigButton(stringResource(R.string.voice_try_again), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
            }
        }
    }
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
private fun PersonHeader(person: VoicePersonUi, onEvent: (VoiceUiEvent) -> Unit, hearName: Boolean = true) {
    val hear = { onEvent(VoiceUiEvent.HearName) }
    Spacer(Modifier.height(PlainphoneSpace.S4))
    PersonPicture(person, if (person.pictureInInk && hearName) 120.dp else 96.dp, onHearName = if (hearName) hear else null)
    // Never cut or shrunk (README, screen titles): a word too long for the line breaks with a hyphen.
    Text(person.name, style = PlainphoneType.Title.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S2).semantics { heading() })
    // The picture carries this action for TalkBack, so the link is hidden there and not offered twice (UX V2).
    if (hearName) Box(Modifier.clearAndSetSemantics {}) { ScreenLink(stringResource(R.string.voice_hear_name), hear) }
    else Spacer(Modifier.height(48.dp))
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

/** At least 96 dp each way: a large symbol beside its word, inside one full-width target. */
@Composable
private fun BigButton(word: String, symbol: Symbol, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp)
        .plainphoneAction(shape = RoundedCornerShape(PlainphoneSpace.BoxRadius), onClick = onClick)
        .padding(horizontal = PlainphoneSpace.S3, vertical = PlainphoneSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4, Alignment.CenterHorizontally)) {
        Box(Modifier.size(80.dp).background(PlainphoneColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
            SymbolMark(symbol, 32.dp, PlainphoneColors.Paper)
        }
        Text(word, style = PlainphoneType.Row, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun SmallButton(word: String, symbol: Symbol?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
        .border(PlainphoneSpace.Border, PlainphoneColors.Ink, RoundedCornerShape(PlainphoneSpace.BoxRadius))
        .plainphoneAction(shape = RoundedCornerShape(PlainphoneSpace.BoxRadius), onClick = onClick)
        .padding(horizontal = PlainphoneSpace.S4, vertical = PlainphoneSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S3, Alignment.CenterHorizontally)) {
        if (symbol != null) SymbolMark(symbol, 20.dp, PlainphoneColors.Ink)
        Text(word, style = PlainphoneType.Button, textAlign = TextAlign.Center)
    }
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
