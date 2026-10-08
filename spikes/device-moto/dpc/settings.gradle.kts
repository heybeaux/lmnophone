// THROWAWAY SPIKE (PLA-5). Not MVP code.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "plainphone-spike-dpc"
include(":app", ":target")
