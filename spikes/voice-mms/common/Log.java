// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458).
package org.plainphone.spike458;

/** Everything the spike observes goes to logcat tag PLA458 (captured into evidence/). */
final class Log {
    private Log() {}
    static void i(String s) { android.util.Log.i("PLA458", s); }
    static void e(String s, Throwable t) { android.util.Log.e("PLA458", s, t); }
}
