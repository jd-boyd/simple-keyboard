plugins {
    id("com.android.application")
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "rkr.simplekeyboard.inputmethod"
        minSdk = 31
        targetSdk = 36
        versionCode = 145
        versionName = "6.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    namespace = "rkr.simplekeyboard.inputmethod"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation-jvm:1.10.0")
}

// ktlint configuration
project.ktlint {
    version.set("1.0.1")
    debug.set(false)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(true)

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Custom formatting tasks
tasks.register("formatCode") {
    description = "Format Kotlin code with ktlint"
    group = "formatting"
    dependsOn("ktlintFormat")
}

tasks.register("checkFormat") {
    description = "Check Kotlin code formatting with ktlint"
    group = "verification"
    dependsOn("ktlintCheck")
}
