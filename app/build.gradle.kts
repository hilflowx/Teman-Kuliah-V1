plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dependency.analysis)
}

android {
  namespace = "com.temankuliah.app"
  compileSdk = 37


  defaultConfig {
    applicationId = "com.temankuliah.app.id"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.1"
  }

  // Tanpa variabel lingkungan apa pun, build debug memakai debug keystore bawaan Android
  // (~/.android/debug.keystore, dibuat otomatis oleh AGP), sehingga clone baru langsung bisa build.
  // Build release butuh KEYSTORE_PATH; lihat README bagian "Build release".
  signingConfigs {
    getByName("debug") {
      val debugKeystore = System.getenv("DEBUG_KEYSTORE_PATH")
      if (!debugKeystore.isNullOrBlank()) {
        storeFile = file(debugKeystore)
        storePassword = System.getenv("DEBUG_STORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "android"
      } else if (rootProject.file("debug.keystore").exists()) {
        storeFile = rootProject.file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    val releaseKeystore = System.getenv("KEYSTORE_PATH")
    if (!releaseKeystore.isNullOrBlank()) {
      create("release") {
        storeFile = file(releaseKeystore)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    warningsAsErrors = true
    abortOnError = true
    enable += setOf("UnusedResources", "UnusedIds")
    baseline = file("$rootDir/lint-baseline.xml")
    textReport = true
    htmlReport = true
  }
}


// Peringatkan bila build release dijalankan tanpa keystore: hasilnya APK/AAB tidak bertanda tangan.
gradle.taskGraph.whenReady {
  val buildingRelease = allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
  if (buildingRelease && System.getenv("KEYSTORE_PATH").isNullOrBlank()) {
    logger.warn("PERINGATAN: KEYSTORE_PATH kosong, build release tidak akan ditandatangani. Lihat README bagian 'Build release'.")
  }
}

// Room menulis skema tiap versi database ke app/schemas/ supaya migrasi bisa diperiksa dan ditinjau.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.adaptive)
  implementation(libs.androidx.compose.material3.adaptive.layout)
  implementation(libs.androidx.compose.material3.adaptive.navigation)
  implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  // Manifest dengan ComponentActivity yang dibutuhkan createComposeRule() pada test Robolectric.
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  ksp(libs.androidx.room.compiler)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    allWarningsAsErrors.set(true)
    freeCompilerArgs.addAll(
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    )
  }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  config.setFrom(files("$rootDir/detekt.yml"))
  baseline = file("$rootDir/detekt-baseline.xml")
  buildUponDefaultConfig = true
  allRules = false
  ignoreFailures = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  reports {
    html.required.set(true)
    xml.required.set(true)
    txt.required.set(false)
    sarif.required.set(false)
  }
}
