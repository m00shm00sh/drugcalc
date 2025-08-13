plugins {
    id("kotlin-jvm-conventions")
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.moshy.drugcalc.calc"
version = "0.0.1"

dependencies {
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))

    implementation(libs.kx.serialization)

    testImplementation(project(":sharedTestLogic"))
    testImplementation(project(":dbTestLogic"))
    testImplementation(project(":calcTestLogic"))
}
