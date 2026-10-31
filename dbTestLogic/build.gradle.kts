plugins {
    id("kotlin-jvm-conventions")
}

group = "com.moshy.drugcalc.dbtest"
version = "0.0.1"

dependencies {
    api(libs.bundles.db.deps)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.junit.jupiter.api)
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))
    implementation(project(":db"))
}
