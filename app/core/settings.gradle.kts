// SPDX-License-Identifier: Apache-2.0
// Temporary lane build; remove when the shared app skeleton lands.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "plainphone-policy-lane"
rootProject.projectDir = file(".standalone")
include(":core", ":policy")
project(":core").projectDir = file(".")
project(":policy").projectDir = file("../policy")
