val kotlin_version: String by project
val ktor_version: String by project
val jquery_version: String by project
val logback_version: String by project
val exposed_version: String by project
val h2_version: String by project
val sqlite_version: String by project
val hikaricp_version: String by project
val hoplite_version: String by project
val aedile_version: String by project
val kproxymap_version: String by project
val kcontainers_version: String by project
val junit_version: String by project
val kx_coroutines_test_version: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.3"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.moshy.drugcalc"
version = "0.0.1-SNAPSHOT"

application {
    mainClass.set("io.ktor.server.jetty.jakarta.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-body-limit")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-webjars-jvm")
    implementation("io.ktor:ktor-server-jetty-jakarta-jvm")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-json:$exposed_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("org.xerial:sqlite-jdbc:$sqlite_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("com.zaxxer:HikariCP:$hikaricp_version")
    implementation("com.sksamuel.aedile:aedile-core:$aedile_version")
    implementation("com.sksamuel.hoplite:hoplite-core:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-datetime:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:$hoplite_version")
    implementation("com.sksamuel.hoplite:hoplite-hocon:$hoplite_version")
    implementation("com.github.m00shm00sh:kproxymap:v$kproxymap_version") // jitpack; version = tag-name
    implementation("com.github.m00shm00sh:kcontainers:v$kcontainers_version") // jitpack; version = tag-name
    testImplementation(kotlin("reflect"))
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("io.ktor:ktor-client-core")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kx_coroutines_test_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation(platform("org.junit:junit-bom:$junit_version"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}