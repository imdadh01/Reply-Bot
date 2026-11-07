plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.replysaathi.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.replysaathi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
