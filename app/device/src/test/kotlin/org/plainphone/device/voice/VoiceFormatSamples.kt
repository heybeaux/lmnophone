// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice

/** A stand-in recording: the format's magic where the format puts it, then some "audio". */
fun VoiceFormat.sample(audio: Int = 320): ByteArray =
    ByteArray(magicOffset) { if (it == magicOffset - 1) 0x18 else 0 } + header + ByteArray(audio) { 1 }
