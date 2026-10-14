// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.policy.*
import org.plainphone.device.approval.*
import org.plainphone.ui.trusted.*
import java.io.File

class AppPermissionsMapperTest {
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val identity = ApprovalBinding(PackageName("org.example.fixture"), setOf(CertDigest("a".repeat(64))))
    private fun read(states: Map<PermissionName, PermissionReadback>) = AppPermissionRead(
        AppPermissionSnapshot(identity, 1, listOf(RequestedPermissionGroup("android.permission-group.CAMERA", "Camera", listOf(camera)))), states)

    @Test fun mapsEveryRealReadbackIncludingMissingAndUnknownWithoutDefaultingToDenied() {
        val expected = listOf(PermissionStatus.Allowed, PermissionStatus.Denied, PermissionStatus.Default, PermissionStatus.Unknown)
        PermissionReadback.entries.zip(expected).forEach { (state, status) ->
            assertEquals(status, appPermissionsUi(read(mapOf(camera to state))).rows.single().status)
        }
        assertEquals(PermissionStatus.Unknown, appPermissionsUi(read(emptyMap())).rows.single().status)
        assertTrue(appPermissionsUi(null).checkFailed)
        assertFalse(appPermissionsUi(read(emptyMap())).checkFailed)
    }
    @Test fun emptyAndMixedAreDistinctFromUnreadable() {
        val empty = AppPermissionRead(AppPermissionSnapshot(identity, 1, emptyList()), emptyMap())
        assertTrue(appPermissionsUi(empty).rows.isEmpty())
        assertFalse(appPermissionsUi(empty).checkFailed)
        val mixed = AppPermissionRead(AppPermissionSnapshot(identity, 1,
            listOf(RequestedPermissionGroup("custom", "Custom access", listOf(camera, mic)))),
            mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED))
        assertEquals(PermissionStatus.Mixed, appPermissionsUi(mixed).rows.single().status)
        assertEquals("Custom access", appPermissionsUi(mixed).rows.single().label)
        assertEquals(PermissionStatus.Unknown, appPermissionsUi(mixed.copy(states = mapOf(camera to PermissionReadback.GRANTED))).rows.single().status)
    }
    @Test fun productionActivityActuallySuppliesReadbackToThePage() {
        val activity = File("src/main/kotlin/org/plainphone/PinAccessActivity.kt").readText()
        val runtime = File("src/main/kotlin/org/plainphone/boot/PolicyRuntime.kt").readText()
        assertTrue(activity.contains("runtime.previewAppPermissions"))
        assertTrue(activity.contains("permissions = appPermissionsUi(state.permissions)"))
        assertTrue(runtime.contains("appPermissions.read(id)"))
        assertTrue(activity.contains("PermissionsScreen(appPermissionChoicesUi(state, appsContext))"))
        assertTrue(activity.contains("runtime.addPreviewApp(app.id, app.identity, selection, choices, pin, active)"))
        assertTrue(runtime.contains("authenticate = pins::authenticateAddition"))
        assertTrue(runtime.contains("open = effects::openPermissionWindow"))
        assertTrue(runtime.contains("write = attempts::write"))
        assertTrue(runtime.contains("preview.hiddenApps().none { it.id == id && it.identity == identity }"))
        assertTrue(runtime.contains("id in preview.protectedApps()"))
        assertTrue(runtime.contains("target.identity.packageName.value !in preview.protectedApps()"))
        val device = File("src/main/kotlin/org/plainphone/preview/Preview2Device.kt").readText()
        assertTrue(device.contains("setOfNotNull(dialer(), sms(), context.packageName, SAFETY_HUB)"))
    }
}
