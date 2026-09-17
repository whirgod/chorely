plugins {
  alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module: the Android SDK is not on its classpath, so
// due-date arithmetic and catch-up cannot reach for a Context, a Room entity or
// SystemClock, and its tests run in milliseconds on the JVM.
kotlin {
  jvmToolchain(17)
  compilerOptions {
    freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
  }
}

dependencies {
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
