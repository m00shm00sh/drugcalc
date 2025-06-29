plugins {
    id("kotlin-jvm-conventions")
}

group = "com.moshy.drugcalc.dbtest"
version = "0.0.1"

dependencies {
    implementation(libs.flyway.core)
    api(libs.bundles.db.deps)
    runtimeOnly(libs.sqlite)
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))
    implementation(project(":db"))
}
