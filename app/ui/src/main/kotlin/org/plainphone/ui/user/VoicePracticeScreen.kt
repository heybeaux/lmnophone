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
data class VoicePersonUi(val id: String, val name: String)
enum class VoiceUiNotice { MaxLength, Interrupted, NothingRecorded, Deleted, CouldNotPlay }
enum class VoiceUiMicProblem { NotAllowed, TurnedOff, DidNotWork }

sealed interface VoiceUiState {
    data class ChoosePerson(val people: List<VoicePersonUi>) : VoiceUiState
    data class Person(val person: VoicePersonUi, val notice: VoiceUiNotice? = null) : VoiceUiState
    data class Recording(val person: VoicePersonUi) : VoiceUiState
    data class Review(val person: VoicePersonUi, val playing: Boolean = false, val notice: VoiceUiNotice? = null) : VoiceUiState
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
}

private enum class Symbol { Record, Stop, Play }

@Composable
fun VoicePracticeScreen(state: VoiceUiState, onEvent: (VoiceUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize().background(PlainphoneColors.Paper)
        .verticalScroll(rememberScrollState()).padding(horizontal = PlainphoneSpace.S6)
        .padding(top = PlainphoneSpace.S2, bottom = PlainphoneSpace.S6),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenLink(stringResource(if (state is VoiceUiState.ChoosePerson) R.string.back_more else R.string.voice_back_people),
            { onEvent(VoiceUiEvent.Back) }, centered = false)
        Text(stringResource(R.string.voice_practice_label), style = PlainphoneType.Button.copy(color = PlainphoneColors.Ink2),
            modifier = Modifier.fillMaxWidth().background(PlainphoneColors.Fill1, RoundedCornerShape(PlainphoneSpace.BoxRadius))
                .padding(horizontal = PlainphoneSpace.S4, vertical = PlainphoneSpace.S2), textAlign = TextAlign.Center)
        when (state) {
            is VoiceUiState.ChoosePerson -> {
                Text(stringResource(R.string.voice_choose), style = PlainphoneType.Title,
                    modifier = Modifier.fillMaxWidth().padding(vertical = PlainphoneSpace.S4).semantics { heading() })
                for (p in state.people) PersonRow(p) { onEvent(VoiceUiEvent.Choose(p.id)) }
            }
            is VoiceUiState.Person -> {
                PersonHeader(state.person, onEvent)
                Notice(state.notice)
                BigButton(stringResource(R.string.voice_record), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
            }
            is VoiceUiState.Recording -> {
                PersonHeader(state.person, onEvent, hearName = false)
                Row(Modifier.padding(vertical = PlainphoneSpace.S4).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
                    SymbolMark(Symbol.Record, 20.dp, PlainphoneColors.Ink)
                    Text(stringResource(R.string.voice_recording), style = PlainphoneType.Row)
                }
                BigButton(stringResource(R.string.voice_stop), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
            }
            is VoiceUiState.Review -> {
                PersonHeader(state.person, onEvent)
                Notice(state.notice)
                if (state.playing) BigButton(stringResource(R.string.voice_stop), Symbol.Stop) { onEvent(VoiceUiEvent.Stop) }
                else BigButton(stringResource(R.string.voice_listen), Symbol.Play) { onEvent(VoiceUiEvent.Play) }
                Spacer(Modifier.height(PlainphoneSpace.S6))
                SmallButton(stringResource(R.string.voice_record_again), Symbol.Record) { onEvent(VoiceUiEvent.RecordAgain) }
                Spacer(Modifier.height(PlainphoneSpace.S4))
                SmallButton(stringResource(R.string.voice_delete), null) { onEvent(VoiceUiEvent.Delete) }
            }
            is VoiceUiState.MicOff -> {
                PersonHeader(state.person, onEvent)
                Text(stringResource(when (state.problem) {
                    VoiceUiMicProblem.NotAllowed -> R.string.voice_mic_not_allowed
                    VoiceUiMicProblem.TurnedOff -> R.string.voice_mic_off
                    VoiceUiMicProblem.DidNotWork -> R.string.voice_mic_error
                }), style = PlainphoneType.Body, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = PlainphoneSpace.S4).semantics { liveRegion = LiveRegionMode.Polite })
                BigButton(stringResource(R.string.voice_try_again), Symbol.Record) { onEvent(VoiceUiEvent.Record) }
            }
        }
    }
}

@Composable
private fun PersonRow(person: VoicePersonUi, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().plainphoneAction(onClick = onClick).heightIn(min = 96.dp)
        .padding(vertical = PlainphoneSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlainphoneSpace.S4)) {
        PersonPicture(person, 72.dp, decorative = true)
        Text(person.name, style = PlainphoneType.App, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PersonHeader(person: VoicePersonUi, onEvent: (VoiceUiEvent) -> Unit, hearName: Boolean = true) {
    Spacer(Modifier.height(PlainphoneSpace.S4))
    PersonPicture(person, 96.dp)
    Text(person.name, style = PlainphoneType.Title, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = PlainphoneSpace.S2).semantics { heading() })
    if (hearName) ScreenLink(stringResource(R.string.voice_hear_name), { onEvent(VoiceUiEvent.HearName) })
    else Spacer(Modifier.height(48.dp))
}

/** Placeholder picture: the person's initial in a monochrome circle. */
@Composable
private fun PersonPicture(person: VoicePersonUi, size: Dp, decorative: Boolean = false) {
    val description = stringResource(R.string.voice_picture, person.name)
    Box(Modifier.size(size).background(PlainphoneColors.Fill2, CircleShape)
        .border(PlainphoneSpace.Border, PlainphoneColors.Ink, CircleShape)
        .then(if (decorative) Modifier.clearAndSetSemantics {} else Modifier.clearAndSetSemantics {
            contentDescription = description
        }), contentAlignment = Alignment.Center) {
        // Sized in dp, not scaled with the text size: it is a picture.
        val letter = with(LocalDensity.current) { (size * 0.5f).toSp() }
        Text(initial(person.name), style = PlainphoneType.Title.copy(fontSize = letter, lineHeight = letter), maxLines = 1)
    }
}

private fun initial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return String(Character.toChars(trimmed.codePointAt(0))).uppercase()
}

@Composable
private fun Notice(notice: VoiceUiNotice?) {
    if (notice == null) { Spacer(Modifier.height(PlainphoneSpace.S4)); return }
    Text(stringResource(when (notice) {
        VoiceUiNotice.MaxLength -> R.string.voice_notice_max
        VoiceUiNotice.Interrupted -> R.string.voice_notice_interrupted
        VoiceUiNotice.NothingRecorded -> R.string.voice_notice_nothing
        VoiceUiNotice.Deleted -> R.string.voice_notice_deleted
        VoiceUiNotice.CouldNotPlay -> R.string.voice_notice_play
    }), style = PlainphoneType.Body, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = PlainphoneSpace.S4).semantics { liveRegion = LiveRegionMode.Polite })
}

/** At least 96 dp each way: a large symbol with its word inside one target. */
@Composable
private fun BigButton(word: String, symbol: Symbol, onClick: () -> Unit) {
    Column(Modifier.widthIn(min = 176.dp).plainphoneAction(shape = RoundedCornerShape(PlainphoneSpace.BoxRadius), onClick = onClick)
        .padding(PlainphoneSpace.S3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PlainphoneSpace.S2)) {
        Box(Modifier.size(104.dp).background(PlainphoneColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
            SymbolMark(symbol, 40.dp, PlainphoneColors.Paper)
        }
        Text(word, style = PlainphoneType.Row, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallButton(word: String, symbol: Symbol?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
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
            Symbol.Play -> drawPath(Path().apply {
                moveTo(this@Canvas.size.width * 0.2f, 0f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
                lineTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height)
                close()
            }, color)
        }
    }
}
