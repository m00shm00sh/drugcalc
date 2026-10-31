plugins {
    id("kotlin-jvm-conventions")
    alias(libs.plugins.jooq.codegen)
}

group = "com.moshy.drugcalc.db"
version = "0.0.1"

dependencies {
    implementation(libs.kproxymap)
    api(libs.bundles.db.deps)
    jooqCodegen(libs.jooq.meta.extensions)
    implementation(project(":sharedLogic"))
    implementation(project(":sharedTypes"))
}

// Migrations that define the tables jOOQ generates code for. Trigger/procedure migrations are
// intentionally excluded because DDLDatabase's parser cannot represent procedural code.
val codegenMigrations = listOf(
    "V001__Create_tables.sql",
    "V002__Add_note_to_blends.sql",
)

jooq {
    configuration {
        generator {
            name = "org.jooq.codegen.KotlinGenerator"

            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    // DDLDatabase's `scripts` property only accepts a single file or a glob, and
                    // globs cannot express "V001 or V002 but not V003". Feeding the concatenated
                    // SQL through `sql` keeps the migration history as the single source of truth.
                    property {
                        key = "sql"
                        value = codegenMigrations.joinToString("\n") {
                            layout.projectDirectory.file("src/main/resources/db/migration/$it").asFile.readText()
                        }
                    }
                    // H2 stores unquoted identifiers in upper case. Lower-case them so the generated
                    // code matches the unquoted (lower-case) identifiers PostgreSQL actually stores.
                    property { key = "defaultNameCase"; value = "lower" }
                }
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

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}
