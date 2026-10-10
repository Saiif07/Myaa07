plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.myraa.app"
    compileSdk = 34
    defaultConfig { applicationId = "com.myraa.app"; minSdk = 24; targetSdk = 34; versionCode = 2; versionName = "0.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
