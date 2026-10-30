plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.fit100.tracker"; compileSdk = 35
    defaultConfig { applicationId = "com.fit100.tracker"; minSdk = 26; targetSdk = 35; versionCode = 11; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
