// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

/**
 * In-memory [PackageSystemPort]. [reads] returns the package list for each read in turn (the last
 * one repeats), so a test can change a package between the two reads of one pass.
 */
class FakePackageSystemPort : PackageSystemPort {
    var model = "XT2415-1"
    var fingerprint = "motorola/cancunn_g/cancunn:15/V1UDS35H.24-58-5/aa:user/release-keys"
    var reads: MutableList<List<RawPackage>> = mutableListOf(emptyList())
    var systemDialer: String? = null
    var defaultDialer: String? = null
    var defaultSms: String? = null
    var webView: String? = null
    var imes: Set<String> = emptySet()
    var inCallUi: Set<String> = emptySet()
    /** Key: action, or "action data" when a data URI is given. */
    val activities = mutableMapOf<String, Set<String>>()
    val receivers = mutableMapOf<String, Set<String>>()
    val browsable = mutableMapOf<String, Set<String>>()
    /** Calls that throw, by method name. */
    val failing = mutableSetOf<String>()
    val calls = mutableListOf<String>()
    var packageReads = 0
    /** Answers [installedForUser]; by default, listed in any read. */
    var userInstalled: (String) -> Boolean = { name -> reads.any { read -> read.any { it.name == name } } }

    fun packages(vararg p: RawPackage) { reads = mutableListOf(p.toList()) }

    private fun <T> call(name: String, value: () -> T): T {
        calls += name
        if (name in failing) throw SecurityException("fake: $name refused")
        return value()
    }

    override fun model() = call("model") { model }
    override fun fingerprint() = call("fingerprint") { fingerprint }
    override fun packages(): List<RawPackage> = call("packages") {
        reads[minOf(packageReads++, reads.size - 1)]
    }
    override fun browsableHandlers(url: String) = call("browsable") { browsable[url].orEmpty() }
    override fun systemDialer() = call("systemDialer") { systemDialer }
    override fun defaultDialer() = call("defaultDialer") { defaultDialer }
    override fun defaultSms() = call("defaultSms") { defaultSms }
    override fun webViewProvider() = call("webViewProvider") { webView }
    override fun enabledInputMethods() = call("enabledInputMethods") { imes }
    override fun systemInCallUiPackages() = call("systemInCallUiPackages") { inCallUi }
    override fun systemOnlyActivities(action: String, data: String?) =
        call("systemOnlyActivities") { activities[if (data == null) action else "$action $data"].orEmpty() }
    override fun systemOnlyReceivers(action: String) = call("systemOnlyReceivers") { receivers[action].orEmpty() }
    override fun installedForUser(name: String) = call("installedForUser") { userInstalled(name) }
}

fun raw(name: String, system: Boolean = false, version: Long = 1, vararg signers: String): RawPackage =
    RawPackage(name, system, version, signers.map { it.toByteArray() })

fun unsigned(name: String, system: Boolean = true, version: Long = 1): RawPackage = RawPackage(name, system, version, null)
