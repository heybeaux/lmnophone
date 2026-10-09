// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Slow hash for PINs and recovery codes. Lane D implements PBKDF2. */
fun interface KeyDerivation {
    fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray
}
