// THROWAWAY SPIKE (PLA-22). Minimal DPC to reproduce the Android 15 hide/unhide + reboot boot loop. Not MVP code.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "plainphone-spike-bootloop"
include(":dpc")
