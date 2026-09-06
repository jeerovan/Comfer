import com.android.build.gradle.internal.dsl.NdkOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val jksProperties = Properties().apply {
    val propertiesFile = rootProject.file("jks-key.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningValue(
    jksProperty: String,
    gradleProperty: String,
    environmentVariable: String,
): String? = jksProperties.getProperty(jksProperty)?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty(gradleProperty).orNull?.takeIf { it.isNotBlank() }
    ?: providers.environmentVariable(environmentVariable).orNull?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue(
    "storeFile",
    "comferUploadStoreFile",
    "COMFER_UPLOAD_STORE_FILE",
)
val releaseStorePassword = releaseSigningValue(
    "storePassword",
    "comferUploadStorePassword",
    "COMFER_UPLOAD_STORE_PASSWORD",
)
val releaseKeyAlias = releaseSigningValue(
    "keyAlias",
    "comferUploadKeyAlias",
    "COMFER_UPLOAD_KEY_ALIAS",
)
val releaseKeyPassword = releaseSigningValue(
    "keyPassword",
    "comferUploadKeyPassword",
    "COMFER_UPLOAD_KEY_PASSWORD",
)
val releaseSigningCredentials = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val configuredReleaseSigningCredentials = releaseSigningCredentials.count { !it.isNullOrBlank() }
require(
    configuredReleaseSigningCredentials == 0 ||
        configuredReleaseSigningCredentials == releaseSigningCredentials.size,
) {
    "Configure all four Comfer upload signing values or none of them"
}
val hasReleaseSigningCredentials = configuredReleaseSigningCredentials ==
    releaseSigningCredentials.size
if (hasReleaseSigningCredentials) {
    require(rootProject.file(requireNotNull(releaseStoreFile)).isFile) {
        "Configured Comfer upload keystore does not exist"
    }
}

android {
    namespace = "com.jeerovan.comfer"
    compileSdk = 37
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "com.jeerovan.comfer"
        minSdk = 24
        targetSdk = 36
        versionCode = 46
        versionName = "46.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.kotlinx.serialization.json)
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
