package com.moshy.drugcalc.cmdclient.io.data

import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import org.jetbrains.annotations.Blocking
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.convertTo
import org.jetbrains.kotlinx.dataframe.io.readCsv
import org.jetbrains.kotlinx.dataframe.io.readExcel
import java.io.File
import java.util.Arrays

@Blocking
internal fun loadCompoundsUpdater(file: String): Map<CompoundName, ProxyMap<CompoundInfo>> =
    @Suppress("MoveLambdaOutsideParentheses")
    decode(
        file,
        ::decodeJsonString,
        { file, type -> loadDataframe<_, _, CompoundsUpdater>(file, type) }
    )

@Blocking
internal fun loadCompounds(file: String): Map<CompoundName, CompoundInfo> =
    @Suppress("MoveLambdaOutsideParentheses")
    decode(
        file,
        ::decodeJsonString,
        { file, type -> loadDataframe<_, _, Compound>(file, type) }
    )

@Blocking
internal fun loadBlends(file: String): Map<BlendName, BlendValue> =
    @Suppress("MoveLambdaOutsideParentheses")
    decode(
        file,
        ::decodeJsonString,
        { file, type -> loadDataframe<_, _, Blend>(file, type) }
    )

@Blocking
internal fun loadFrequencies(file: String): Map<FrequencyName, FrequencyValue> =
    @Suppress("MoveLambdaOutsideParentheses")
    decode(
        file,
        ::decodeJsonString,
        { file, type -> loadDataframe<_, _, Frequency>(file, type) }
    )

internal fun loadData(file: String): Data =
    decode(file, ::decodeJsonString, ::loadComplexDataframe)

private enum class DfType {
    EXCEL,
    CSV,
    /** extended csv with `!section ` marker(s) */
    XCSV,
}

@Blocking
private inline fun <reified R> decode(filename: String, onJson: (String) -> R, onDataframe: (String, DfType) -> R): R {
    val fileStart: ByteArray = File(filename).inputStream().use {
        it.readNBytes(64)
    }
    return when {
        fileStart.matchesJson() ->
            File(filename).readText().let { return onJson(it) }
        // within the scope of Kotlin DataFrame, this only matches XLS files so XLS vs DOC is not a consideration
        fileStart.matchesOleComContainer() ->
            onDataframe(filename, DfType.EXCEL)
        // within the scope of Kotlin DataFrame, this only matches XLSX files so XLSX vs ODT is not a consideration
        fileStart.matchesZipContainer() ->
            onDataframe(filename, DfType.EXCEL)
        // we assume a comma is in the first 64 bytes of the header line
        fileStart.matchesCsv() -> {
            // assume xcsv has !section in first line
            val df =
                if (fileStart.matchesXcsv())
                    DfType.XCSV
                else
                    DfType.CSV
            onDataframe(filename, df)
        }
        else ->
            error("couldn't determine file type (tried XLS header, XLSX header, CSV header, JSON heuristic)")
    }
}

private inline fun <reified R> decodeJsonString(s: String) =
    JsonWithLenientIsoDuration.decodeFromString<R>(s)

private fun ByteArray.matchesJson(): Boolean =
    first() in JSON_START

private val JSON_START = "[{".toByteArray()

private fun ByteArray.matchesOleComContainer(): Boolean =
    this startsWith COM_CONTAINER

private val COM_CONTAINER = listOf(0xD0, 0xCF, 0x11, 0xE0).map(Int::toByte).toByteArray()

// note: empty and spanned archives are not recognized by design
private fun ByteArray.matchesZipContainer(): Boolean =
    this startsWith ZIP_HEADER

private val ZIP_HEADER = "PK\u0003\u0004".toByteArray()

private fun ByteArray.matchesXcsv(): Boolean =
    this startsWith XCSV_MARK_SECTION

private val XCSV_MARK_SECTION = "!section ".toByteArray()

private fun ByteArray.matchesCsv(): Boolean =
    ','.code.toByte() in this

private infix fun ByteArray.startsWith(other: ByteArray) =
    Arrays.equals(this, 0, other.size, other, 0, other.size)

private inline fun <K : Any, V : Any, reified M : Any> loadDataframe(file: String, type: DfType): Map<K, V> =
    when (type) {
        DfType.EXCEL -> DataFrame.readExcel(file)
        DfType.XCSV -> throw IllegalArgumentException("bad input: detected XCSV")
        DfType.CSV -> DataFrame.readCsv(file)
    }
        .convertTo<M>()
        .let { dfToMap(it) }

private fun loadComplexDataframe(filename: String, type: DfType) =
    when (type) {
        DfType.EXCEL -> {
            val compounds: Map<CompoundName, CompoundInfo> = tryGetExcelSheet<Compound, _, _>(filename, "compounds")
            val blends: Map<BlendName, BlendValue> = tryGetExcelSheet<Blend, _, _>(filename, "blends")
            val frequencies: Map<FrequencyName, FrequencyValue> = tryGetExcelSheet<Frequency, _, _>(filename, "frequencies")
            Data(compounds, blends, frequencies)
        }
        DfType.XCSV -> {
            val chunks = readXcsv(filename)
            val compounds: Map<CompoundName, CompoundInfo> = tryGetXcsvChunk<Compound, _, _>(chunks, "compounds")
            val blends: Map<BlendName, BlendValue> = tryGetXcsvChunk<Blend, _, _>(chunks, "blends")
            val frequencies: Map<FrequencyName, FrequencyValue> = tryGetXcsvChunk<Frequency, _, _>(chunks, "frequencies")
            Data(compounds, blends, frequencies)
        }
        DfType.CSV -> throw IllegalArgumentException("bad input: detected normal CSV")
    }

private data class CsvChunk(
    val physicalLineOffset: Int,
    val data: String
)

private fun readXcsv(filename: String): Map<String, CsvChunk> =
    buildMap {
        File(filename).useLines { lines ->
            var cur: String? = null
            var builder = StringBuilder()
            lines.forEachIndexed { n, l ->
                if (l.startsWith("!section ")) {
                    cur = l.substring("!section ".length)
                    builder = StringBuilder()
                    // +1 to convert index to line number,
                    // +1 to advance to start of chunk data
                    putIfAbsent(cur, (n + 2) to builder)?.let {
                        throw IllegalArgumentException("section specified twice: $cur")
                    }
                } else {
                    require(cur != null) {
                        "non-section line encountered"
                    }
                    if (builder.isNotEmpty())
                        builder.append('\n')
                    builder.append(l)
                }
            }
        }
    }.mapValues { (_, v) -> CsvChunk(v.first, v.toString()) }

private inline fun <reified F : Any, K, V> tryGetXcsvChunk(chunks: Map<String, CsvChunk>, chunk: String): Map<K, V> =
    chunks[chunk]?.let { (off, data) ->
        val df = DataFrame.readCsv(data).convertTo<F>()
        dfToMap(df, off)
    }
        ?: emptyMap()

private inline fun <reified F : Any, K, V> tryGetExcelSheet(filename: String, sheetName: String): Map<K, V> =
    try {
        val df = DataFrame.readExcel(filename, sheetName).convertTo<F>()
        dfToMap(df)
    } catch (e: IllegalStateException) { // DataFrame uses error() where requireNotNull() would've been more appropriate
        /* check against the specific error of sheet not found so we don't return empty map on
         * valid sheet but invalid contents
         */
        if (e.message?.let { it.startsWith("Sheet with name ") && it.endsWith(" not found") } ?: false)
            emptyMap()
        else
            throw IllegalArgumentException(e.message)
    }