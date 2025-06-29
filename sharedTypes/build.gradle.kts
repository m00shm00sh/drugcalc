group = "com.moshy.drugcalc.types"
version = "0.0.1"

plugins {
    id("kotlin-jvm-conventions")
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    implementation(libs.kx.serialization)
    implementation(libs.kproxymap)
    implementation(project(":sharedLogic"))
    testImplementation(project(":sharedTestLogic"))
}