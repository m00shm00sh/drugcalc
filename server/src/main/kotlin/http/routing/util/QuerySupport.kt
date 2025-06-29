package com.moshy.drugcalc.server.http.routing.util

import com.moshy.drugcalc.calc.datacontroller.PaginationSpecifier
import com.moshy.drugcalc.types.calccommand.CycleDescription
import kotlinx.serialization.modules.SerializersModule

internal fun buildCycleDescriptionFromTokens(
    flags: List<String>?,
    compoundOrBlend: List<String>,
    variantOrTransformer: List<String>?,
    dose: List<String>,
    start: List<String>,
    duration: List<String>,
    freqName: List<String>,
    serializersModule: SerializersModule
): List<CycleDescription> {
    // the first mandatory argument is compoundOrBlend so that's used for determining count
    val len1 = compoundOrBlend.size

    return buildList {
        for (i in 0..<len1) {
            val tokens = buildList {
                add(flags?.let { it[i].mapFlag() } ?: "")
                add(compoundOrBlend[i])
                add(variantOrTransformer?.let { it[i] } ?: "")
                add(dose[i])
                add(start[i])
                add(duration[i])
                add(freqName[i])
            }
            try {
                val decoder = StringIterDecoder(tokens, serializersModule)
                add(decoder.decodeTo<CycleDescription>())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("at position $i: $e", e)
            }
        }
    }
}

private fun String.mapFlag(): String? =
    when (this) {
        "b" -> CycleDescription.PREFIX_BLEND
        "t" -> CycleDescription.PREFIX_TRANSFORMER
        else -> CycleDescription.PREFIX_COMPOUND
    }

internal fun <T : Any> paginationSpecifier(from: T?, limit: Int?): PaginationSpecifier<T>? {
    if (from == null)
        return null
    @Suppress("LocalVariableName") val ASC = PaginationSpecifier.Direction.AFTER
    if (limit == null)
        return PaginationSpecifier(seekAfter = from, direction = ASC)
    return PaginationSpecifier(limit = limit, seekAfter = from, direction = ASC)
}