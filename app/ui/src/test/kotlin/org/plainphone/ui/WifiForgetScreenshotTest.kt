// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.ui
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.user.*
@RunWith(Parameterized::class)
class WifiForgetScreenshotTest(private val scale: Float) {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth=360, screenHeight=640, xdpi=160, ydpi=160,
        density=Density.MEDIUM, fontScale=scale, locale="en"), showSystemUi=false, maxPercentDifference=0.0)
    @Test fun connected() = page(false)
    @Test fun locationOff() = page(true)
    @Test fun cleanupFailed() = page(false, cleanupFailed = true)
    @Test fun noIdentity() = page(true, failed = true)
    @Test fun signIn() = message(WifiProblem.SignInRequired)
    @Test fun noInternet() = message(WifiProblem.NoInternet)
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun page(locationOff: Boolean, cleanupFailed: Boolean = false, failed: Boolean = false) = paparazzi.snapshot {
        PlainphoneTheme {
            val view=LocalView.current as ViewRootForTest
            var forget=0
            Box(Modifier.onGloballyPositioned {
                val nodes=view.semanticsOwner.getAllSemanticsNodes(mergingEnabled=true)
                fun action(label:String) = nodes.single { n -> n.config.getOrElse(SemanticsProperties.Text){emptyList()}.any{it.text==label} }
                if (!failed) action("Forget this network").config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(!failed, forget>0)
            }) { HomeTestFrame { WifiSettingsScreen(WifiSettingsUiState(connected=true,network="Sam",
                nearby=listOf("Sam","Jo"), locationOff=locationOff, canForget=!failed, forgetFailed=failed, locationOffFailed=cleanupFailed,
                internetStatus=null), {}, onForget={forget++}) } }
        }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun message(problem:WifiProblem) = paparazzi.snapshot {
        PlainphoneTheme {
            val view=LocalView.current as ViewRootForTest
            val events=mutableListOf<WifiMessageEvent>()
            Box(Modifier.onGloballyPositioned {
                val nodes=view.semanticsOwner.getAllSemanticsNodes(mergingEnabled=true)
                val texts=nodes.flatMap{it.config.getOrElse(SemanticsProperties.Text){emptyList()}}.map{it.text}
                assertTrue("Calls and texts still work" in texts)
                assertTrue("when the phone has signal (the bars at the top of the screen)." in texts)
                assertFalse("Forget this network" in texts)
                for(label in listOf("OK")) {
                    val node=nodes.single{it.config.getOrElse(SemanticsProperties.Text){emptyList()}.any{it.text==label}}
                    assertTrue(node.boundsInRoot.bottom<=592f)
                    node.config[SemanticsActions.OnClick].action!!.invoke()
                }
                assertFalse(WifiMessageEvent.ForgetNetwork in events);assertTrue(WifiMessageEvent.Ok in events)
            }) { HomeTestFrame { WifiMessageScreen(WifiMessageUiState(problem, canForget=false),events::add) } }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="fontScale={0}")
        fun scales()=listOf(1f,1.3f,1.5f,1.8f,2f).map{arrayOf(it)}
    }
}
