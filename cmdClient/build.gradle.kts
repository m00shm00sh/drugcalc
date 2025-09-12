plugins {
    id("kotlin-jvm-conventions")
    application
    alias(libs.plugins.kotlin.plugin.dataframe)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.moshy.drugcalc.cmdclient"
version = "0.0.1"

application {
    mainClass = "com.moshy.drugcalc.cmdclient.ApplicationKt"
}

dependencies {
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))

    implementation(kotlin("reflect"))
    implementation(libs.kx.dataframe)
    implementation(libs.plotly.kt)
//  do not directly include kx-html; it will conflict with PlotlyKt's kx-html version and throw a MethodNotFound
//    implementation(libs.kx.html)
    implementation(libs.logback.classic)
    implementation(libs.bundles.ktor.client)
    implementation(libs.krepl)

    testImplementation(libs.kx.coroutines.test)
    testImplementation(project(":sharedTestLogic"))
}

tasks.shadowJar {
    mergeServiceFiles()
}