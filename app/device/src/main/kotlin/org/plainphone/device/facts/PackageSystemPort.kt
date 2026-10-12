// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

/**
 * One installed package as PackageManager reported it in one read.
 * @property signers DER encodings of `SigningInfo.apkContentsSigners` (current signers only, never the
 *   rotation history); null when Android returned no signing info.
 * @property enabled `ApplicationInfo.enabled`: false for a disabled (or disabled-until-used) package.
 */
class RawPackage(
    val name: String,
    val system: Boolean,
    val versionCode: Long,
    val signers: List<ByteArray>?,
    val enabled: Boolean = true,
)

/**
 * The live OS reads behind a policy pass. Every package-level answer includes hidden and disabled
 * packages of the calling user. Package names are returned exactly as Android reports them.
 */
interface PackageSystemPort {
    /** `Build.MODEL`. */
    fun model(): String
    /** `Build.FINGERPRINT`. */
    fun fingerprint(): String

    /** Every package installed for this user, hidden and disabled ones included. */
    fun packages(): List<RawPackage>
    /** Packages with an activity for `ACTION_VIEW` + `BROWSABLE` on [url] (`MATCH_ALL`, hidden included). Never fetched. */
    fun browsableHandlers(url: String): Set<String>

    /** `TelecomManager.getSystemDialerPackage()`. */
    fun systemDialer(): String?
    /** `TelecomManager.getDefaultDialerPackage()`: the `ROLE_DIALER` holder. */
    fun defaultDialer(): String?
    /** `Telephony.Sms.getDefaultSmsPackage()`: the `ROLE_SMS` holder. */
    fun defaultSms(): String?
    /** `WebView.getCurrentWebViewPackage()`. */
    fun webViewProvider(): String?
    /** Packages of the enabled input methods. */
    fun enabledInputMethods(): Set<String>
    /** Packages with an `InCallService` that declares `android.telecom.IN_CALL_SERVICE_UI`, `MATCH_SYSTEM_ONLY`. */
    fun systemInCallUiPackages(): Set<String>
    /** Packages with an activity for [action] (and [data], if given), `MATCH_SYSTEM_ONLY`, hidden included. */
    fun systemOnlyActivities(action: String, data: String? = null): Set<String>
    /** Packages with a receiver for [action], `MATCH_SYSTEM_ONLY`, hidden included. */
    fun systemOnlyReceivers(action: String): Set<String>

    /**
     * Whether [name] is installed for this user, asked on its own. True for hidden, disabled and
     * suspended packages. False only when Android positively answers that it is not: the name is
     * unknown, or it exists on the device with `FLAG_INSTALLED` unset for this user (for example after
     * `pm uninstall --user 0`). Throws when the answer cannot be read; a throw is never absence.
     */
    fun installedForUser(name: String): Boolean
}
