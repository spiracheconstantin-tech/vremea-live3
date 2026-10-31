plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ro.vremea.wallpaper"
    compileSdk = 34
    defaultConfig {
        applicationId = "ro.vremea.wallpaper"
        minSdk = 24
        targetSdk = 34
        val vc = (project.findProperty("vCode") as String?)?.toIntOrNull() ?: 1
        versionCode = vc
        versionName = "build $vc"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
