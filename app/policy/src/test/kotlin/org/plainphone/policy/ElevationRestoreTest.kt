// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ElevationRestoreTest {
    private val identity = ApprovalBinding(PackageName("test.app"), setOf(CertDigest("ab".repeat(32))))
    private val targets = listOf(ElevationTarget.Play, ElevationTarget.Settings, ElevationTarget.Approve(identity),
        ElevationTarget.Update(StagedUpdateBinding("cd".repeat(32), 2)))
    private val now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 100))

    @Test fun `machine countdowns and grants round trip through public fields for every target`() {
        for (target in targets) {
            val counting = ElevationMachine.request(ElevationState(), "id", target, ElevationLimits(), now).state
            val c = counting.countdown!!
            val restored = assertNotNull(ElevationCountdown.restore(c.id, c.target, c.startedAt, c.durationMillis))
            assertEquals(listOf(c.id, c.target, c.startedAt, c.durationMillis),
                listOf(restored.id, restored.target, restored.startedAt, restored.durationMillis))
            val ready = now.copy(boot = BootTime("boot", 600_100))
            val g = ElevationMachine.start(counting, "id", target, ElevationLimits(), ready).state.grant!!
            for (open in if (g.packageAccessOpen) listOf(true, false) else listOf(false)) {
                val decoded = assertNotNull(ElevationGrant.restore(g.id, g.target, g.startedAt, g.deadline, open, g.approved))
                assertEquals(listOf(g.id, g.target, g.startedAt, g.deadline, open, g.approved),
                    listOf(decoded.id, decoded.target, decoded.startedAt, decoded.deadline, decoded.packageAccessOpen, decoded.approved))
            }
        }
    }

    @Test fun `countdown invalid duration ID and both overflow boundaries return null`() {
        for (duration in listOf(Long.MIN_VALUE, -1, 0, 599_999, 86_400_001, Long.MAX_VALUE))
            assertNull(ElevationCountdown.restore("id", ElevationTarget.Play, now.boot, duration))
        assertNull(ElevationCountdown.restore(" ", ElevationTarget.Play, now.boot, 600_000))
        for (elapsed in listOf(Long.MAX_VALUE, Long.MAX_VALUE - 600_000))
            assertNull(ElevationCountdown.restore("id", ElevationTarget.Play, BootTime("boot", elapsed), 600_000))
        assertNotNull(ElevationCountdown.restore("id", ElevationTarget.Play, BootTime("boot", Long.MAX_VALUE - 900_000), 600_000))
        assertNotNull(ElevationCountdown.restore("id", ElevationTarget.Play, now.boot, 86_400_000))
    }

    @Test fun `grant rejects invalid ID boot deadline scope and duplicate package approvals`() {
        fun decode(id: String = "id", target: ElevationTarget = ElevationTarget.Play,
            deadline: BootTime = BootTime("boot", 600_100), open: Boolean = false,
            approved: Set<ApprovalBinding> = emptySet()) =
            ElevationGrant.restore(id, target, now.boot, deadline, open, approved)
        assertNull(decode(id = ""))
        for (deadline in listOf(BootTime("other", 600_100), BootTime("boot", 100), BootTime("boot", 99),
            BootTime("boot", 600_101), BootTime("boot", Long.MAX_VALUE))) assertNull(decode(deadline = deadline))
        for (target in targets.drop(1)) assertNull(decode(target = target, approved = setOf(identity)))
        for (target in targets.drop(2)) assertNull(decode(target = target, open = true))
        val resigned = ApprovalBinding(identity.packageName, setOf(CertDigest("cd".repeat(32))))
        assertNull(decode(approved = setOf(identity, resigned)))
    }

    @Test fun `restored Play approval set is a defensive immutable snapshot`() {
        val source = mutableSetOf(identity)
        val grant = assertNotNull(ElevationGrant.restore("id", ElevationTarget.Play, now.boot, BootTime("boot", 600_100), false, source))
        source.clear()
        assertEquals(setOf(identity), grant.approved)
        assertFailsWith<UnsupportedOperationException> { (grant.approved as MutableSet).clear() }
    }
}
