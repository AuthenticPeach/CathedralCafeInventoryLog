// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
 //   alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    dependencies {
        // Use the correct KSP version
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.8.22-1.0.11")
    }
}

allprojects {

}
