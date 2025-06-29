package com.moshy.drugcalc.calctest

import com.moshy.drugcalc.common.*
import com.moshy.drugcalc.types.datasource.*
import com.moshy.drugcalc.types.dataentry.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.*


suspend fun populateDatabase(source: DataSourceDelegate): Boolean {
    if (!source.isDbEmpty())
        return false
    withContext(PreferredIODispatcher()) {
        ::DATA.invoke()
    }
    val modified =
        source.putBulk(
            DATA.compounds,
            DATA.blends,
            DATA.frequencies
        )
    return modified
}

internal val DATA by lazy {
    val logger = logger("calcTest: LoadInitData")
    try {
        val file = object{}::class.java.getResource("/init-data.json")
        requireNotNull(file) {
            "couldn't open resource"
        }
        val contents = file.readText().let {
                JsonWithLenientIsoDuration.decodeFromString<Data>(it)
        }
        require(contents.compounds.isNotEmpty()) {
            "no compounds"
        }
        require(contents.blends.isNotEmpty()) {
            "no blends"
        }
        require(contents.frequencies.isNotEmpty()) {
            "no frequencies"
        }
        contents
    } catch (e: Throwable) {
        val msgCause =
            exceptionMessage.firstOrNull { it.first.isInstance(e) }?.second
        if (msgCause != null) {
            logger.error("couldn't {}: {}", msgCause, lazyPrintable { e.message ?: "(null message)" })
        } else
            logger.error("unexpected error reading data", e)
        throw e
    }
}
private val exceptionMessage by lazy {
    listOf(
        IOException::class to "open init data file",
        SerializationException::class to "deserialize data file",
        IllegalArgumentException::class to "use data",
    )
}