plugins {
    id("kotlin-jvm-conventions")
}

group = "com.moshy.drugcalc.calctest"
version = "0.0.1"

dependencies {
    implementation(libs.kx.serialization)
    implementation(libs.kx.coroutines.test)
    implementation(project(":sharedTypes"))
    implementation(project(":sharedLogic"))
    implementation(project(":dbTestLogic"))
    implementation(project(":calc"))
}