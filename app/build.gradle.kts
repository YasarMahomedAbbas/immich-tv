plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Release signing comes from env vars set by CI, never from the repo.
val keystorePath: String? = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "io.github.yasarmahomedabbas.immichtv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yasarmahomedabbas.immichtv"
        minSdk = 26
        targetSdk = 35
        // CI passes these from the git tag; local builds get a dev version.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.0.0-dev"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

// Coverage is measured on the domain and data layers and the ViewModels. Compose screens and
// the activity need a device, so they are left out.
kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable")
                packages("io.github.yasarmahomedabbas.immichtv.ui.theme")
                classes(
                    "*.MainActivity*",
                    "*.ImmichTvApp",
                    "*.AppContainer*",
                    "*.ComposableSingletons*",
                    "*Screen*",
                    // Thin wrappers over the AndroidKeyStore and SharedPreferences: they need a
                    // device. The logic behind them is tested with fakes.
                    "*.KeystoreCipher*",
                    "*.SharedPreferencesStorage*",
                    // Coil's interceptor and image loader need an Android context; the URL and
                    // cache key they use are pure functions in ImmichImages, tested there.
                    "*.PreviewInterceptor*",
                )
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
