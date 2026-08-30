import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.compose.screenshot)
}

// Release signing — values live in app/keystore.properties (git-ignored).
// Absent on machines without the upload key (e.g. CI for debug builds); the
// release signingConfig simply stays unconfigured in that case.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.nexflow"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adsf.nexflow"
        minSdk = 30
        targetSdk = 37
        versionCode = 9
        versionName = "1.5.0"

        testInstrumentationRunner = "com.nexflow.HiltTestRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Full build for GitHub Releases / sideload — keeps SMS & phone-call features.
        create("github") {
            dimension = "distribution"
        }
        // Google Play build — SMS/Call permissions and code are excluded (Play restricts
        // SMS/Call permissions to default handler apps). See src/github vs src/play.
        create("play") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Bundle native debug symbols (from dependency .so files, e.g. androidx.graphics.path)
            // so Play can symbolicate native crash/ANR stack traces. Clears the Play Console
            // "no debug symbols" warning.
            ndk {
                debugSymbolLevel = "FULL"
            }
            // Only attach the signing config when the keystore is present, so
            // release builds on machines without it still configure (just unsigned).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Paired with android.experimental.enableScreenshotTest in gradle.properties.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates verbose Gemini request/response logging in GeminiClient.
        buildConfig = true
    }
    testOptions {
        // android.util.Log used in GeminiClient/AiChatOrchestrator becomes a no-op in JVM
        // unit tests instead of throwing "not mocked".
        unitTests.isReturnDefaultValues = true

        // Robolectric needs the merged resources/manifest to inflate real strings and themes.
        unitTests.isIncludeAndroidResources = true

        // Emulators CI can boot on its own, so connected tests stop depending on someone
        // having a phone plugged in. ATD ("automated test device") images ship without the
        // launcher and most system apps — they boot far faster and are the image type Google
        // recommends for headless instrumentation runs.
        managedDevices {
            localDevices {
                // minSdk floor. Most of the platform behaviour NexFlow leans on (scoped
                // storage, exact alarms, notification listeners) is at its oldest here.
                create("api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
                // Newest ATD image available; there is no aosp-atd for API 37 yet, so this is
                // as close to compileSdk as a managed device gets.
                create("api36") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "aosp-atd"
                }
            }
            groups {
                create("ci") {
                    targetDevices.add(localDevices.getByName("api30"))
                    targetDevices.add(localDevices.getByName("api36"))
                }
            }
        }
    }

    lint {
        // Lint runs as a CI gate, so a regression has to break the build instead of scrolling
        // past in the log. Pre-existing findings are parked in lint-baseline.xml; delete
        // entries from it as they get fixed, never regenerate it to silence something new.
        abortOnError = true
        // checkDependencies pulls :core:* into the same run — otherwise the core modules,
        // which have no lint task wired into CI of their own, would never be checked.
        checkDependencies = true
        baseline = file("lint-baseline.xml")
        warningsAsErrors = false
        // These three query the network for newer artifact versions, so their findings change
        // whenever an upstream release happens rather than when this repo does. Baselining
        // them would mean a baseline that rots on its own; leaving them on would mean lint
        // needs network in CI. Dependency freshness is a deliberate decision here anyway —
        // see the KSP/Kotlin version note in libs.versions.toml.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // Core modules
    implementation(project(":core:automation"))
    implementation(project(":core:flow-schema"))
    implementation(project(":core:macrodroid-compat"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    // AppCompat — provides AppCompatDelegate.setApplicationLocales for in-app
    // language switching with backport to API 30–32 (per-app language prefs).
    implementation(libs.androidx.appcompat)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Ktor (GitHub API)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // WorkManager + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.work)

    // Glance Widget (Phase 2)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Drag-to-reorder
    implementation(libs.reorderable)

    // Google Location Services (Geofencing)
    implementation(libs.play.services.location)

    // Image cropper (wallpaper action — aspect-locked crop UI)
    implementation(libs.android.image.cropper)

    // Coil (contributor avatar images on the About screen)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testRuntimeOnly(libs.junit5.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // Robolectric layer: Compose UI and navigation tests that need an Android runtime but no
    // device. See app/src/test/resources/robolectric.properties for the SDK level they run at.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    // MigrationTestHelper. The Room Gradle plugin's schemaDirectory feeds app/schemas into
    // androidTest assets, which is where the helper looks for the exported schema JSON.
    androidTestImplementation(libs.room.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    // Compose Preview screenshot tests — see app/src/screenshotTest.
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
