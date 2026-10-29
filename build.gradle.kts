plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Această bucată de cod rulează ÎNAINTE ca pluginul de Android să verifice fișierele și șterge forțat orice dublură
val mainDir = file("main")
val manifestFile = file("main/AndroidManifest.xml")
if (mainDir.exists()) {
    // Ne asigurăm că folderul există și scriem un manifest complet steril, fără elemente problematice
    manifestFile.parentFile.mkdirs()
    manifestFile.writeText("""
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
            // Îi indicăm fișierul pe care l-am securizat mai sus
            manifest.srcFile("main/AndroidManifest.xml")
            java.srcDirs("main/java")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
}

// Ștergem fișierele manifest duplicat din folderele temporare înainte ca procesul de îmbinare să înceapă
gradle.taskGraph.whenReady {
    allTasks.forEach { task ->
        if (task.name.startsWith("processDebugMainManifest") || task.name.startsWith("processReleaseMainManifest")) {
            task.doFirst {
                val badFile = file("app/src/main/AndroidManifest.xml")
                if (badFile.exists()) badFile.delete()
                val badFile2 = file("src/main/AndroidManifest.xml")
                if (badFile2.exists()) badFile2.delete()
            }
        }
    }
}
