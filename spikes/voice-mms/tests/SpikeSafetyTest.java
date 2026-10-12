// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY (PLA-458): host-only guard tests.
package org.plainphone.spike458;

public final class SpikeSafetyTest {
    public static void main(String[] args) {
        for (String hardware : new String[] {"goldfish", "ranchu"}) {
            SpikeSafety.requireEmulatorHardware(hardware);
        }
        for (String hardware : new String[] {null, "", "qcom", "ranchu-phone"}) {
            rejected(() -> SpikeSafety.requireEmulatorHardware(hardware));
        }
        for (int suffix = 0; suffix < 100; suffix++) {
            SpikeSafety.requireFakeRecipient(String.format("55501%02d", suffix));
        }
        for (String recipient : new String[] {null, "", "5550000", "5550200", "5550100;5550101", "sms:5550100"}) {
            rejected(() -> SpikeSafety.requireFakeRecipient(recipient));
        }
        for (int seconds : new int[] {1, 5, 60}) {
            SpikeSafety.requireDuration(seconds);
        }
        for (int seconds : new int[] {-1, 0, 61, Integer.MAX_VALUE}) {
            rejected(() -> SpikeSafety.requireDuration(seconds));
        }
        for (String name : new String[] {"voice.amr", "voice.m4a", "send.pdu"}) {
            SpikeSafety.requireSharedFile(name);
        }
        for (String name : new String[] {null, "", "../voice.amr", "other.amr", "voice.amr/extra", "../voice.m4a", "voice.m4a/extra"}) {
            rejected(() -> SpikeSafety.requireSharedFile(name));
        }
        System.out.println("Spike safety: emulator, 100 fictional recipients, durations and shared files PASS");
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Unsafe input was accepted");
    }
}
