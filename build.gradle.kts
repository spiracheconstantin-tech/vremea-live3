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

// Forțează rescrierea curată a Manifestului la fiecare build pentru a elimina erorile de fișiere corupte
tasks.register("cleanAndFixManifest") {
    doFirst {
        val manifestFile = file("main/AndroidManifest.xml")
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://android.com"
                package="ro.vremea.wallpaper">

                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
                <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
                <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
                <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="Vremea Live"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true">

                    <activity
                        android:name="ro.vremea.wallpaper.MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>

                    <service
                        android:name="ro.vremea.wallpaper.WeatherWallpaperService"
                        android:permission="android.permission.BIND_WALLPAPER"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.service.wallpaper.WallpaperService" />
                        </intent-filter>
                        <meta-data
                            android:name="android.service.wallpaper"
                            android:resource="@xml/wallpaper" />
                    </service>

                    <receiver
                        android:name="ro.vremea.wallpaper.WeatherWidget"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                        </intent-filter>
                        <meta-data
                            android:name="android.appwidget.provider"
                            android:resource="@xml/widget_info" />
                    </receiver>

                </application>
            </manifest>
        """.trimIndent())
    }
}

// Execută curățarea automată înainte ca proiectul să fie evaluat de parserul de Manifest
tasks.matching { it.name.startsWith("processDebugMainManifest") || it.name.startsWith("preBuild") }.configureEach {
    dependsOn("cleanAndFixManifest")
}
