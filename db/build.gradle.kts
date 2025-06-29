plugins {
    id("kotlin-jvm-conventions")
    alias(libs.plugins.flyway)
    alias(libs.plugins.jooq.codegen)
}

group = "com.moshy.drugcalc.db"
version = "0.0.1"

dependencies {
    implementation(libs.kproxymap)
    api(libs.bundles.db.deps)
    jooqCodegen(libs.sqlite)
    runtimeOnly(libs.sqlite)
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))
}

flyway {
    url = "jdbc:sqlite:${projectDir}/data/data.db"
}

jooq {
    configuration {
        jdbc {
            driver = "org.sqlite.JDBC"
            url = "jdbc:sqlite:${projectDir}/data/data.db"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"

            database {
                name = "org.jooq.meta.sqlite.SQLiteDatabase"
                excludes = "flyway_schema_history"
            }
            target {
                packageName = "com.moshy.drugcalc.db.generated"
                directory = "build/generated-jooq"
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].apply {
        kotlin.srcDir("build/generated-jooq")
    }
}

tasks.named("jooqCodegen") {
    dependsOn("flywayMigrate")
}

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}