plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

// A release build takes its version from the tag the pipeline was triggered by, passed
// in as `-Pchorely.versionName`. A local build has no tag, hence the placeholder.
val releaseVersionName = providers.gradleProperty("chorely.versionName").getOrElse("1.0-dev")

// Android refuses to install an APK whose versionCode is below the installed one, so the
// code has to rise with the name. major.minor.patch is packed three digits to a field
// (1.2.3 -> 1002003) and derived from the name rather than tracked by hand, so rebuilding
// a tag rebuilds the same code. Any `-prerelease` suffix is dropped: it does not order.
val releaseVersionParts = releaseVersionName.substringBefore('-').split('.')
val versionPart = { index: Int -> releaseVersionParts.getOrNull(index)?.toIntOrNull() ?: 0 }
val versionMajor = versionPart(0)
val versionMinor = versionPart(1)
val versionPatch = versionPart(2)

// Overflowing a field collides with the field above it — 1.2.1000 and 1.3.0 would both
// pack to 1003000 — and a code an installed build already carries installs over nothing.
// The collision is silent, so refuse the version instead of shipping it. release.yml
// rejects the same tags earlier; this is the backstop for a hand-run release build.
require(versionMinor in 0..999 && versionPatch in 0..999) {
    "chorely.versionName '$releaseVersionName': minor and patch must each be at most 999"
}
// versionCode is an Int on the platform and 2147.999.999 would overflow it, so major is
// capped just below that — a flat bound rather than one that depends on the other fields.
// 2146.999.999 is therefore the largest version, and the arithmetic below cannot overflow.
require(versionMajor in 0..2146) {
    "chorely.versionName '$releaseVersionName': major must be at most 2146, " +
        "since versionCode is an Int"
}
val releaseVersionCode = versionMajor * 1_000_000 + versionMinor * 1_000 + versionPatch
require(releaseVersionCode >= 1) {
    "chorely.versionName '$releaseVersionName': 0.0.0 has no valid versionCode"
}

// The pipeline decodes the keystore outside the workspace and points this at it. Without
// it there is no release signing config at all, which is what a local `./gradlew build`
// wants — it must not need the secrets to assemble an (unsigned) release.
val releaseKeystore = providers.environmentVariable("CHORELY_KEYSTORE_FILE").orNull?.let(::file)

android {
    namespace = "at.woergoetter.chorely"
    compileSdk = 36
    defaultConfig {
        applicationId = "at.woergoetter.chorely"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("CHORELY_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("CHORELY_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("CHORELY_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // Null when the keystore is absent, which is AGP's own default: an unsigned APK.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  // UI and reminders talk to the domain's interfaces; :core:data is present
  // only so Hilt can bind its adapters at the composition root.
  implementation(project(":core:domain"))
  implementation(project(":core:data"))

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Dependency injection
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.hilt.compiler)

  // Reminder scheduling
  implementation(libs.androidx.work.runtime.ktx)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.work.testing)
  androidTestImplementation(libs.hilt.android.testing)
  kspAndroidTest(libs.hilt.compiler)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
