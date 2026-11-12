plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vbtdroid.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vbtdroid.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"

        ndk {
            abiFilters += "arm64-v8a"
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            // Personal MVP: sign with the local debug key so the release APK installs and
            // updates over debug builds. Replace with a dedicated key before distribution.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("tflite", "task")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.opencv)
    implementation(libs.tflite)
    implementation(libs.tflite.gpu)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
