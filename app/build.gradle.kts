plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // Do not specify a version here
    alias(libs.plugins.compose)
}


android {
    namespace = "com.invenkode.cathedralcafeinventorylog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.invenkode.cathedralcafeinventorylog"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "1.8"
        apiVersion = "1.8"
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Compose BOM ensures consistent versions.
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Firebase dependencies.
    implementation(platform("com.google.firebase:firebase-bom:32.2.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics")

    // Room dependencies.
    implementation(libs.androidx.room.runtime.v251)
    implementation(libs.androidx.room.ktx)
    ksp("androidx.room:room-compiler:2.5.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Kotlin Coroutines.
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager.
    implementation(libs.androidx.work.runtime.ktx)

    // Other UI and support libraries.
    implementation(libs.androidx.recyclerview)
    implementation("androidx.core:core-ktx:1.9.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.coordinatorlayout)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.material)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

apply(plugin = "com.google.gms.google-services")
