plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("AP98-release-key.jks")
            storePassword = "AP98Keystore!2025"   // Must match what you set
            keyAlias = "AP98"
            keyPassword = "AP98Key!2025"          // Must match the key password
        }
    }
    namespace = "com.invenkode.cathedralcafeinventorylog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.invenkode.cathedralcafeinventorylog"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["release"]
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }
}

dependencies {
    // Room runtime (version managed via your version catalog)
    implementation(libs.androidx.room.runtime.v251)
    implementation(libs.androidx.room.ktx)
    // KSP processor for Room
    ksp("androidx.room:room-compiler:2.5.1")
    // Add Room KTX for coroutine support
    implementation("androidx.room:room-ktx:2.6.1")
    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)

    // JavaMail dependencies
    implementation(libs.android.mail)
    implementation(libs.mail.android.activation)

    // RecyclerView and core UI libraries
    implementation(libs.androidx.recyclerview)
    implementation("androidx.core:core-ktx:1.9.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Additional UI and support libraries
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.room.runtime)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.material)
}

// Force jvmTarget = "11" for all Kotlin compile tasks (including KSP tasks)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
