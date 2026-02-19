plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

hilt {
    enableAggregatingTask = false
}

android {
    namespace = "com.etfmonitor"
    compileSdk = 36
    ndkVersion = "29.0.13113456"

    defaultConfig {
        applicationId = "com.etfmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Enable JUnit5 for unit tests
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module"
            )
        }
    }
}


dependencies {
    // kotlin_krx integration
    implementation("com.krxkt:kotlin-krx")

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)  // ✅ 추가
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // JSON parsing
    implementation(libs.kotlinx.serialization.json)

    // Vico Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // MPAndroidChart for oscillator charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // CardView for marker layouts
    implementation("androidx.cardview:cardview:1.0.0")

    // WorkManager for scheduled tasks
    implementation(libs.androidx.work.runtime.ktx)

    // OkHttp for Claude API
    implementation(libs.okhttp)

    // Jsoup for HTML parsing (Naver Finance scraping)
    implementation("org.jsoup:jsoup:1.17.2")

    // Security - Encrypted SharedPreferences for API key storage
    implementation(libs.androidx.security.crypto)

    // Google Play Services & Drive API
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client)
    implementation(libs.google.api.client.gson)
    implementation(libs.google.drive.api)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Testing - Unit Tests
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")

    // Testing - Instrumented Tests (Android)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.truth.ext)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
}