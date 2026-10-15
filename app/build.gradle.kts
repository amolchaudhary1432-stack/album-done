plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Each cloud build gets a higher version number so the phone accepts it as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.albumdone.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.albumdone.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Model files must stay uncompressed so they can be read straight from the app file.
    androidResources {
        noCompress += listOf("task", "tflite", "onnx")
    }

    signingConfigs {
        // Test-only key so every test build installs over the previous one.
        // The Play Store release will use a separate, private upload key.
        create("test") {
            storeFile = file("testkey.jks")
            storePassword = "albumdone"
            keyAlias = "albumdone"
            keyPassword = "albumdone"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
            // Real phones only. Leaving out x86 keeps the app much smaller.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        debug {
            signingConfig = signingConfigs.getByName("test")
            // MediaPipe ships no x86 build, so the emulator test runs the phone (ARM) build
            // through Android's built-in ARM translation.
            ndk { abiFilters += listOf("arm64-v8a") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // On-device face landmarks + expressions and object detection (Apache 2.0). Models are bundled.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
    // Runs the SFace face-matching model on the phone (MIT licence).
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")

    // Tests that run the real app on an Android emulator (see .github/workflows/build.yml).
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.02"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
