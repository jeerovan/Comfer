import com.android.build.gradle.internal.dsl.NdkOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jeerovan.comfer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jeerovan.comfer"
        minSdk = 23
        targetSdk = 36
        versionCode = 25
        versionName = "25.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enables code-related app optimization.
            isMinifyEnabled = true
            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = NdkOptions.DebugSymbolLevel.SYMBOL_TABLE.toString()
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.billing.ktx)
    implementation(libs.purchases)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(libs.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.reorderable)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}