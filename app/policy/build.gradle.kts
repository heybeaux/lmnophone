// SPDX-License-Identifier: Apache-2.0
plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(libs.versions.jdk.get().toInt()) }
dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test-junit"))
}
