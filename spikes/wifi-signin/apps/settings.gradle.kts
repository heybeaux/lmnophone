// THROWAWAY SPIKE (PLA-18). Copy of the PLA-11 emulator DPC plus a Wi-Fi sign-in session. Not MVP code.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "plainphone-spike-wifi-signin"
include(":dpc")
