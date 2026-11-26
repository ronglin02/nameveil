import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val appVersion = providers.gradleProperty("nameveilVersion").get()
val generateVersion by tasks.registering {
    val output = layout.buildDirectory.dir("generated/nameveilVersion")
    inputs.property("version", appVersion)
    outputs.dir(output)
    doLast {
        val source = output.get().file("com/ronglin/nameveil/AppVersion.kt").asFile
        source.parentFile.mkdirs()
        source.writeText("package com.ronglin.nameveil\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}

kotlin {
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    sourceSets {
        commonMain.get().kotlin.srcDir(generateVersion)
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        androidMain.get().kotlin.srcDir("src/jvmShared/kotlin")

        val desktopMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.ronglin.nameveil"
    compileSdk = 35

    val releaseKeystorePath = providers.environmentVariable("NAMEVEIL_KEYSTORE_PATH").orNull
    val releaseKeystorePassword = providers.environmentVariable("NAMEVEIL_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("NAMEVEIL_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("NAMEVEIL_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.ronglin.nameveil"
        minSdk = 26
        targetSdk = 35
        val (major, minor, patch) = appVersion.split('.').map(String::toInt)
        versionCode = major * 10000 + minor * 100 + patch
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

compose.desktop {
    application {
        mainClass = "com.ronglin.nameveil.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NameVeil"
            packageVersion = appVersion
            // Keep native-package metadata ASCII-only: Windows jpackage reads its
            // generated argument file with the active system code page.
            description = "Offline cross-platform filename privacy tool"
            vendor = "Ronglin"
            windows.menuGroup = "NameVeil"
            windows.shortcut = true
            windows.iconFile.set(project.file("src/desktopMain/resources/nameveil-icon-generated.ico"))
            linux.shortcut = true
            linux.iconFile.set(project.file("src/desktopMain/resources/nameveil-icon-generated.png"))
        }
    }
}
