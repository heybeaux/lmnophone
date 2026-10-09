// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

/** Stands in for process death at a write point; nothing in the store may catch it. */
class SimulatedCrash : RuntimeException("simulated crash")
