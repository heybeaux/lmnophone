// THROWAWAY SPIKE (PLA-11). Not MVP code.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "plainphone-spike-emulator"
include(":dpc", ":fx-browser", ":fx-applinks", ":fx-httponly", ":fx-urlbar", ":fx-emergency")
