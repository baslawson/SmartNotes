import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.baslawson.smartnotes"
    compileSdk = 36

    defaultConfig {
        // Permanent: Android treats a different ID as a different app.
        applicationId = "io.github.baslawson.smartnotes"
        minSdk = 26 // java.time works natively from API 26
        targetSdk = 36 // Android 16, as on the Samsung Galaxy S26
        versionCode = 13
        versionName = "0.4.1"
    }

    // The release key lives outside the repository. keystore.properties (git-ignored) points at it;
    // without that file a release build is simply left unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        // Debug builds install alongside the release app instead of replacing it.
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // Screenshot tests run on Robolectric, which needs the app's resources.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Screenshots are always saved (to look at), never compared.
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

// The database schema of each version is kept in app/schemas, so later versions can write and test migrations.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Reminders written in notes (@2026-11-20 14:00) are scheduled as WorkManager jobs.
    implementation(libs.androidx.work.runtime)
    // GitHub releases for Settings → Updates.
    implementation(libs.okhttp)
    implementation(libs.reorderable)

    // Plain JVM tests (app/src/test): note parsing, search, shopping list, the log file and updates.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
