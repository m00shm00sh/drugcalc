plugins {
    id("kotlin-jvm-conventions")
    application
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.moshy.drugcalc.server"
version = "0.0.1"

application {
    mainClass = "com.moshy.drugcalc.server.ApplicationKt"
}

dependencies {
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))
    implementation(project(":db"))
    implementation(project(":calc"))

    implementation(libs.logback.classic)

    implementation(libs.bundles.ktor.server)

    implementation(libs.bundles.hoplite)

    implementation(libs.pem.keystore)

    testImplementation(libs.bundles.ktor.server.test)
    testImplementation(libs.kx.coroutines.test)
    testImplementation(project(":sharedTestLogic"))
    testImplementation(project(":dbTestLogic"))
    testImplementation(project(":calcTestLogic"))
}

tasks.shadowJar {
    mergeServiceFiles()
}