pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "9.5.2"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        id("com.google.devtools.ksp") version "2.3.14"
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { google(); mavenCentral() }
}
rootProject.name = "MeuTreino"
include(":app")
