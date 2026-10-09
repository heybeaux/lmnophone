// SPDX-License-Identifier: Apache-2.0
plugins { kotlin("jvm") version "2.1.20" }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test-junit"))
}
