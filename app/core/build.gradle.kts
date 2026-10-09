// SPDX-License-Identifier: Apache-2.0
plugins { kotlin("jvm") version "2.1.20" }
kotlin { jvmToolchain(17) }
dependencies { testImplementation(kotlin("test-junit")) }
