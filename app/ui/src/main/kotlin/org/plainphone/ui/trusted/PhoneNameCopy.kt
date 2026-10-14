// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui.trusted

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource as s
import org.plainphone.ui.R
import org.plainphone.ui.components.withoutBidiControls

/** Copy that says "{user}'s phone", with its plain "this phone" variant for when no name is set (PLA-583). */
enum class PhoneNameCopy(@StringRes val named: Int, @StringRes val unnamed: Int) {
    AddConfirm(R.string.admin_add_confirm, R.string.admin_add_confirm_unnamed),
    MenuTitle(R.string.admin_menu_title, R.string.admin_menu_title_unnamed),
    PinBody(R.string.admin_pin_body, R.string.admin_pin_body_unnamed),
    TurnOffIntro(R.string.off_intro, R.string.off_intro_unnamed),
    TurnOffSure(R.string.off_sure_body, R.string.off_sure_body_unnamed),
}

/** The user's configured name, or null when none is set: blank, or the neutral fallback words. */
fun configuredUserName(userName: String, fallback: String): String? =
    userName.withoutBidiControls().takeUnless { it.isBlank() || it.trim() == fallback.trim() }

/** [copy] with the configured name last in its arguments, or the "this phone" variant with only [before]. */
@Composable
fun phoneNameText(copy: PhoneNameCopy, userName: String, vararg before: Any): String {
    val name = configuredUserName(userName, s(R.string.current_phone_user))
    return if (name == null) s(copy.unnamed, *before) else s(copy.named, *before, name)
}
