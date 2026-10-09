// THROWAWAY SPIKE (PLA-38). Not MVP code.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "plainphone-spike-grayscale"
include(":dpc")
