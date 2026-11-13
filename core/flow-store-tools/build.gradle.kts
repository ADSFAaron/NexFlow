// The flow store's command-line tool: validates submissions, dry-runs them, and builds the signed
// index's input. Runs on a plain JVM in the store repo's CI, so it may depend only on the
// Android-free core modules.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.nexflow.store.tools.MainKt")
    applicationName = "flow-store-tools"
}

dependencies {
    implementation(project(":core:flow-schema"))
    implementation(project(":core:automation"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
