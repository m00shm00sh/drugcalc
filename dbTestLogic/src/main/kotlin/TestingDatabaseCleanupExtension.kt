package com.moshy.drugcalc.dbtest

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Drops the ephemeral PostgreSQL databases created through [getTestingDataSource] after each test.
 *
 * Registered automatically via `META-INF/services/org.junit.jupiter.api.extension.Extension` and
 * JUnit's extension autodetection (enabled in the shared JVM test conventions).
 */
class TestingDatabaseCleanupExtension : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
        TestPostgres.cleanup()
    }
}
