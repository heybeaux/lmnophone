// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY: isolated feasibility probe, never imported by the app.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../app/gradle/libs.versions.toml")) } }
}
rootProject.name = "communication-counts-spike"
include(":probe")
