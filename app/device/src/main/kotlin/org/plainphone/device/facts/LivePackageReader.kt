// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import java.security.MessageDigest
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.PackageFactsSource
import org.plainphone.device.ReapplyTimings
import org.plainphone.links.PackageFacts
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.InstalledPackage

object CertDigests {
    /** SHA-256 of one DER-encoded certificate: the digest `apksigner verify --print-certs` prints. */
    fun sha256(der: ByteArray): CertDigest =
        CertDigest(hex(MessageDigest.getInstance("SHA-256").digest(der)))

    /** Same lowercase bytes as %02x, without constructing a Formatter for each byte. */
    internal fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xff
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }

    private const val HEX = "0123456789abcdef"
}

/**
 * One pass's package facts, in the shapes each module takes.
 * @property facts one [PackageFacts] per package, for `:links`.
 * @property installed for `:policy`; a package whose facts changed between the two reads appears once per variant.
 * @property identities for `PolicyInput.installedIdentities`; conflicting signer sets appear once each.
 * @property disabled packages disabled in both reads of the pass.
 */
data class LivePackages(
    val facts: List<PackageFacts>,
    val installed: List<InstalledPackage>,
    val identities: List<ApprovalBinding>,
    val disabled: Set<PackageName> = emptySet(),
)

/**
 * Reads package facts from the live OS, hidden packages included. Nothing here is cached: a digest
 * is a fact for this pass only and never grants access by itself (PLA-46).
 *
 * The package list is read twice, before and after the other queries of the pass. A package whose
 * system flag, version or signers differ between the reads is reported once per variant. `:policy`
 * fails closed on such duplicates, so contradicting facts confer neither system identity nor an
 * approval match. Its [PackageFacts] is not system and has no signers.
 */
class LivePackageReader(
    private val port: PackageSystemPort,
    private val timings: ReapplyTimings = ReapplyTimings(),
) : PackageFactsSource {
    override fun installedPackages(): List<PackageFacts> = read().facts

    fun read(between: () -> Unit = {}): LivePackages {
        val first = timings.measure("livePackages.snapshot1") { port.packages().map(::fact) }
        val (http, https) = timings.measure("livePackages.probes") {
            port.browsableHandlers(PROBE_HTTP) to port.browsableHandlers(PROBE_HTTPS)
        }
        between()
        val second = timings.measure("livePackages.snapshot2") { port.packages().map(::fact) }
        return timings.measure("livePackages.merge") { merge(first, second, http, https) }
    }

    private fun merge(first: List<Fact>, second: List<Fact>, http: Set<String>, https: Set<String>): LivePackages {
        val byName = LinkedHashMap<String, MutableList<Fact>>()
        for (f in first + second) byName.getOrPut(f.name) { mutableListOf() }.let { if (f !in it) it += f }

        val facts = mutableListOf<PackageFacts>()
        val installed = mutableListOf<InstalledPackage>()
        val identities = mutableListOf<ApprovalBinding>()
        val disabled = mutableSetOf<PackageName>()
        for ((name, variants) in byName) {
            val pkg = PackageName(name)
            val agreed = variants.map { it.system to it.signers }.distinct().singleOrNull()
            facts += PackageFacts(
                packageName = pkg,
                signingCerts = agreed?.second.orEmpty(),
                isSystem = agreed?.first == true,
                handlesProbeHttp = name in http,
                handlesProbeHttps = name in https,
            )
            variants.map { InstalledPackage(pkg, it.system, it.versionCode) }.distinct().forEach { installed += it }
            val signerSets = variants.map { it.signers }.distinct()
            if (signerSets.none { it.isEmpty() }) signerSets.forEach { identities += ApprovalBinding(pkg, it) }
            if (variants.none { it.enabled }) disabled += pkg
        }
        return LivePackages(facts, installed, identities, disabled)
    }

    private data class Fact(val name: String, val system: Boolean, val versionCode: Long, val signers: Set<CertDigest>, val enabled: Boolean)

    private fun fact(raw: RawPackage) =
        Fact(raw.name, raw.system, raw.versionCode, raw.signers.orEmpty().map(CertDigests::sha256).toSet(), raw.enabled)

    companion object {
        const val PROBE_HTTP = "http://${PackageFacts.PROBE_HOST}/"
        const val PROBE_HTTPS = "https://${PackageFacts.PROBE_HOST}/"
    }
}
