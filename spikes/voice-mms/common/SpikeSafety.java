// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY (PLA-458): confines this probe to emulator fixtures.
package org.plainphone.spike458;

public final class SpikeSafety {
    private SpikeSafety() {}

    public static void requireEmulatorHardware(String hardware) {
        if (!"ranchu".equals(hardware) && !"goldfish".equals(hardware)) {
            throw new IllegalArgumentException("This spike runs on an emulator only");
        }
    }

    public static void requireFakeRecipient(String recipient) {
        if (recipient == null || !recipient.matches("55501[0-9]{2}")) {
            throw new IllegalArgumentException("Only fictional 555-01xx recipients are allowed");
        }
    }

    public static void requireDuration(int seconds) {
        if (seconds < 1 || seconds > 60) {
            throw new IllegalArgumentException("Use 1 to 60 seconds of synthetic audio");
        }
    }

    public static void requireSharedFile(String name) {
        if (!"voice.amr".equals(name) && !"send.pdu".equals(name)) {
            throw new IllegalArgumentException("Only the two spike fixtures can be shared");
        }
    }
}
