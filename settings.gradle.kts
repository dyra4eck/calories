pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // zxing-android-embedded (сканер штрих-кодов) публикуется на JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "Calories"
include(":app")
