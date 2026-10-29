plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ro.vremea.wallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "ro.vremea.wallpaper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("main/AndroidManifest.xml")
            java.srcDirs("main/java")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}

// Această funcție rulează chiar în faza incipientă a Gradle și scrie direct un fișier curat pe mașina virtuală
val initManifest = providers.provider {
    val file = file("main/AndroidManifest.xml")
    file.parentFile.mkdirs()
    file.writeText("""
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://android.com"
            package="ro.vremea.wallpaper">
            <uses-permission android:name="android.permission.INTERNET" />
            <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
            <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
            <application
                android:allowBackup="true"
                android:label="Vremea Live"
                android:supportsRtl="true">
                <activity
                    android:name="ro.vremea.wallpaper.MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent())
    ""
}

// Legăm execuția de configurarea proiectului
gradle.settingsEvaluated {
    initManifest.get()
}
// Forțăm evaluarea timpurie
initManifest.get()
