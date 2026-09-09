plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.jeerovan.fixtures"
    compileSdk = 37
    defaultConfig { applicationId = "com.jeerovan.fixtures"; minSdk = 24; targetSdk = 37; versionCode = 1; versionName = "1" }
    flavorDimensions += "source"
    productFlavors {
        create("mail") { dimension = "source"; applicationIdSuffix = ".mail"; manifestPlaceholders["fixtureLabel"] = "Fixture Mail" }
        create("chat") { dimension = "source"; applicationIdSuffix = ".chat"; manifestPlaceholders["fixtureLabel"] = "Fixture Chat" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }
