// SPDX-License-Identifier: Apache-2.0
// Temporary lane build until the shared app skeleton and lane A's :core land on main.
// Delete this file and core-shim/ when rebasing onto the skeleton; :links then uses the real :core.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "plainphone-links-lane"
rootProject.projectDir = file(".standalone")
include(":links", ":core")
project(":links").projectDir = file(".")
project(":core").projectDir = file("core-shim")
