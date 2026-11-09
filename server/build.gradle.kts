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
    // OpenAPIConfig exposes swagger-codegen types but ktor-server-openapi declares them runtime-only.
    implementation(libs.swagger.codegen)

    implementation(libs.bundles.hoplite)

    testImplementation(libs.bundles.ktor.server.test)
    testImplementation(libs.kx.coroutines.test)
    testImplementation(project(":sharedTestLogic"))
    testImplementation(project(":dbTestLogic"))
    testImplementation(project(":calcTestLogic"))
}

tasks.shadowJar {
    mergeServiceFiles()
}

/**
 * Regenerates the committed OpenAPI specification from the Ktor routing tree.
 *
 * The generator lives in the test source set so it can build the routing tree with a stub data
 * source instead of requiring a live database. Run with:
 * `./gradlew :server:generateOpenApiSpec`
 *
 * Override the address advertised in the generated document with:
 * `./gradlew :server:generateOpenApiSpec -PopenApiServerAddress=https://api.example.com`
 */
tasks.register<JavaExec>("generateOpenApiSpec") {
    group = "documentation"
    description = "Regenerate src/main/resources/openapi/documentation.yaml from the Ktor routes"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.moshy.drugcalc.server.openapi.GenerateOpenApiSpecKt")

    val outputFile = layout.projectDirectory.file("src/main/resources/openapi/documentation.yaml").asFile.absolutePath
    val serverAddress = providers.gradleProperty("openApiServerAddress")
        .orElse("http://0.0.0.0:8080")
    inputs.property("openApiServerAddress", serverAddress)
    args(outputFile, serverAddress.get())
}
