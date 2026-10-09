// THROWAWAY SPIKE (PLA-11). Browser classifier from spec web-containment, run against fixtures.
// `skipIdentity = true` is the negative control: it drops the "Plainphone first, never-hide first" checks.
package com.plainphone.spike.emu

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.security.MessageDigest

object Classifier {
    /** Reviewed catalog (spike stand-in). */
    val CATALOG = mapOf(
        "com.android.chrome" to "GENERAL_BROWSER",
        "com.plainphone.fx.urlbar" to "CATALOG_REVIEW",
    )
    /** Hidden by name, never approvable (spec web-containment). */
    val NAMED_HIDE = setOf(
        "com.google.android.googlequicksearchbox", "com.google.android.apps.bard",
    )
    /** Static never-hide list for the spike; the floor is added at runtime. */
    val NEVER_HIDE_STATIC = setOf(
        "com.google.android.gms", "com.android.vending", "com.google.android.gsf",
        "com.google.android.apps.messaging", "com.google.android.dialer", "com.android.phone",
        "com.android.systemui", "android", "com.google.android.permissioncontroller",
    )

    fun certDigest(c: Context, pkg: String): String? = runCatching {
        val info = c.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val sig = info.signingInfo!!.apkContentsSigners.first().toByteArray()
        MessageDigest.getInstance("SHA-256").digest(sig).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun probe(c: Context, scheme: String): Set<String> {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://plainphone-probe.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return c.packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
    }

    /** Returns one line per interesting package; hides when [apply]. */
    fun run(c: Context, apply: Boolean, skipIdentity: Boolean): String {
        val pm = c.packageManager
        val selfDigest = certDigest(c, c.packageName)
        val http = probe(c, "http")
        val https = probe(c, "https")
        val neverHide = NEVER_HIDE_STATIC + Floor.all(Floor.compute(c))
        val pkgs = pm.getInstalledPackages(0).map { it.packageName }
        val interesting = pkgs.filter {
            it in http || it in https || it in CATALOG || it in NAMED_HIDE || it.startsWith("com.plainphone.") ||
                it == "com.google.android.dialer" || it == "com.google.android.gms" || it == "com.android.vending"
        }.sorted()
        val out = StringBuilder("probe http=${http.sorted()} https=${https.sorted()}\n")
        for (p in interesting) {
            val looksBrowser = (p in http && p in https) || CATALOG[p] == "GENERAL_BROWSER"
            val verdict = when {
                !skipIdentity && p == c.packageName && certDigest(c, p) == selfDigest -> "SELF_SKIPPED"
                !skipIdentity && p in neverHide && looksBrowser -> "NEVER_HIDE_CONFLICT_REPORTED"
                !skipIdentity && p in neverHide -> "NEVER_HIDE"
                p in NAMED_HIDE -> "NAMED_HIDE"
                p in http && p in https -> "GENERAL_BROWSER(heuristic)"
                CATALOG[p] == "GENERAL_BROWSER" -> "GENERAL_BROWSER(catalog)"
                CATALOG[p] == "CATALOG_REVIEW" -> "FLAG_CATALOG_REVIEW"
                p in http || p in https -> "NOT_BROWSER(one scheme only)"
                else -> "NOT_BROWSER"
            }
            var action = ""
            if (apply && (verdict.startsWith("GENERAL_BROWSER") || verdict == "NAMED_HIDE")) {
                action = " hide->" + Emu.dpm(c).setApplicationHidden(Emu.admin(c), p, true)
            }
            out.append("$p: $verdict$action\n")
        }
        return out.toString()
    }
}
