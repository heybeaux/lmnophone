// THROWAWAY SPIKE (PLA-18). Supervised Wi-Fi sign-in session: journal, relax, close, revoke. Not MVP code.
package com.plainphone.spike.emu

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object Signin {
    const val PKG = "com.google.android.captiveportallogin"
    private val main = Handler(Looper.getMainLooper())

    /** Last Wi-Fi network seen by the Wi-Fi-specific callback (works even when Wi-Fi is not the default network). */
    @Volatile var wifiNet: Network? = null
    @Volatile var wifiCaptive = false
    @Volatile var wifiValidated = false

    fun active(c: Context) = Emu.prefs(c).contains("j_pkgs")

    private fun t() = "uptime=${SystemClock.elapsedRealtime()}ms"

    /** Step 1 writes the journal (commit, not apply). Steps 2-3 relax only after it is on disk. */
    fun start(c: Context, seconds: Long): String {
        if (active(c)) return "ALREADY_ACTIVE " + state(c)
        val dpm = Emu.dpm(c); val admin = Emu.admin(c)
        val prevLt = dpm.getLockTaskPackages(admin).toList()
        val deadline = System.currentTimeMillis() + seconds * 1000
        val ok = Emu.prefs(c).edit()
            .putString("j_pkgs", PKG)
            .putString("j_prev_lt", prevLt.joinToString(","))
            .putLong("j_deadline", deadline)
            .putLong("j_started", System.currentTimeMillis())
            .commit()
        if (!ok) return "JOURNAL_WRITE_FAILED (nothing relaxed)"
        dpm.setLockTaskPackages(admin, (prevLt + PKG).distinct().toTypedArray())
        val unhid = dpm.setApplicationHidden(admin, PKG, false)
        main.postDelayed({ if (active(c) && System.currentTimeMillis() >= deadline) end(c, "deadline") }, seconds * 1000 + 200)
        Emu.log("signin start ${t()} journal=ok unhide=$unhid deadlineIn=${seconds}s")
        return "OK session started; " + state(c)
    }

    fun end(c: Context, reason: String): String {
        val p = Emu.prefs(c)
        if (!active(c)) return "NO_SESSION ($reason)"
        val dpm = Emu.dpm(c); val admin = Emu.admin(c)
        val hid = dpm.setApplicationHidden(admin, PKG, true)
        val prev = p.getString("j_prev_lt", "").orEmpty().split(",").filter { it.isNotBlank() && it != PKG }
        dpm.setLockTaskPackages(admin, (prev + c.packageName).distinct().toTypedArray())
        p.edit().remove("j_pkgs").remove("j_prev_lt").remove("j_deadline").remove("j_started").commit()
        Emu.log("signin end reason=$reason ${t()} hide=$hid hiddenNow=${dpm.isApplicationHidden(admin, PKG)}")
        return "OK session ended ($reason) hide=$hid; " + state(c)
    }

    /** Called first on every Plainphone start (Application.onCreate and boot receivers). */
    fun revokeOnStart(c: Context, where: String) {
        if (!Emu.isDeviceOwner(c)) return
        if (active(c)) Emu.log("signin revoke-on-start where=$where ${t()} -> " + end(c, "revoke:$where"))
        else Emu.log("signin revoke-on-start where=$where ${t()} journal empty; hidden=${Emu.dpm(c).isApplicationHidden(Emu.admin(c), PKG)}")
    }

    fun state(c: Context): String {
        val dpm = Emu.dpm(c); val admin = Emu.admin(c)
        val p = Emu.prefs(c)
        val left = if (active(c)) (p.getLong("j_deadline", 0) - System.currentTimeMillis()) / 1000 else 0
        return "active=${active(c)} secondsLeft=$left hidden=${dpm.isApplicationHidden(admin, PKG)} " +
            "inLockTask=${PKG in dpm.getLockTaskPackages(admin)} lockTaskState=${c.getSystemService(ActivityManager::class.java).lockTaskModeState} " +
            "wifiNet=$wifiNet wifiCaptive=$wifiCaptive wifiValidated=$wifiValidated"
    }

    /** Ways a Device Owner might open the sign-in page itself. */
    fun open(c: Context, how: String): String {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val net = wifiNet ?: return "NO_WIFI_NETWORK"
        return when (how) {
            // Hidden/system API; reflection to show what a normal app gets.
            "reflect" -> runCatching {
                cm.javaClass.getMethod("startCaptivePortalApp", Network::class.java).invoke(cm, net); "returned normally"
            }.getOrElse { "threw ${(it.cause ?: it).javaClass.simpleName}: ${(it.cause ?: it).message}" }
            // Public action constant, but no CaptivePortal extra (its constructor is not public).
            "intent" -> {
                val i = Intent(ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN).setPackage(PKG)
                    .putExtra(ConnectivityManager.EXTRA_NETWORK, net)
                    .putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL, "http://connectivitycheck.gstatic.com/generate_204")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { c.startActivity(i); "startActivity returned normally" }
                    .getOrElse { "threw ${it.javaClass.simpleName}: ${it.message}" }
            }
            // Public: ask the system to re-check; re-posts the "Sign in" notification.
            "recheck" -> { cm.reportNetworkConnectivity(net, false); "reportNetworkConnectivity(wifi,false) called" }
            else -> "UNKNOWN open mode $how"
        }
    }

    /** Wi-Fi-specific callback (Q7 detection, Q5 close-on-validated) plus screen-off close. */
    fun register(c: Context) {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                wifiNet = network
                wifiCaptive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                wifiValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Emu.log("wifi-cb net=$network captive=$wifiCaptive validated=$wifiValidated ${t()}")
                if (wifiValidated && active(c)) main.post { Emu.log("signin close-on-validated -> " + end(c, "validated")); home(c) }
            }
            override fun onLost(network: Network) {
                if (network == wifiNet) { wifiNet = null; wifiCaptive = false; wifiValidated = false }
                Emu.log("wifi-cb lost $network")
            }
        })
        c.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) { if (active(ctx)) { Emu.log("signin screen-off -> " + end(ctx, "screen-off")) } }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    fun home(c: Context) {
        c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}
