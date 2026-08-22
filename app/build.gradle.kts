plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.revio.social"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.revio.social"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.0"

        testInstrumentationRunner = "com.revio.social.HiltTestRunner"
        buildConfigField(
            "String",
            "WEB_CLIENT_ID",
            "\"${property("WEB_CLIENT_ID")}\""
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${property("DEBUG_API_BASE_URL")}\""
            )
            // Separate Firebase project (revio-debug-47037) and OAuth Web client from release —
            // see docs/firebase-environments.md. Overrides the WEB_CLIENT_ID field declared in
            // defaultConfig for this build type only; release's field is untouched.
            buildConfigField(
                "String",
                "WEB_CLIENT_ID",
                "\"${property("DEBUG_WEB_CLIENT_ID")}\""
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${property("RELEASE_API_BASE_URL")}\""
            )
            // pas 1.9: niciun keystore de release există/e autorizat în acest mediu — decizia e
            // să rămânem pe semnarea debug pentru closed testing (Play App Signing poate ridica
            // ulterior propria cheie de upload); schimbarea la un keystore de producție rămâne
            // un pas separat, explicit.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // mockk-android pulls junit-jupiter transitively; several of its jars ship the same
            // license/notice files, which collide when androidTest's APK is merged.
            excludes += "META-INF/LICENSE*.md"
            excludes += "META-INF/NOTICE*.md"
        }
    }
}

kapt {
    correctErrorTypes = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.exifinterface)
    implementation(libs.hilt.core)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.compose.foundation)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    // Coil 3 needs an explicit network engine to load remote (http) images.
    implementation(libs.coil.network.okhttp)
    implementation(libs.serialization.converter)

    implementation(libs.haze)

    // Local persistence (Room)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Pas 6.4: Compose UI tests mock EarlySpotterController (a concrete class, no interface) —
    // mockk-android is the instrumented-test-compatible variant of mockk used elsewhere in tests.
    androidTestImplementation(libs.mockk.android)
    // Pas 6: instrumented navigation test drives the four real screens (Hilt ViewModels) through
    // RevioNavigation with a TestNavHostController.
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.navigation.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
