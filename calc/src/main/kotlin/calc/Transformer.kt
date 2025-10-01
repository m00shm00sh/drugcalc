package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.containers.circularIterator
import kotlinx.coroutines.channels.ticker

/** Returns a transformer that applies [blockTransform] to each block.
 *
 * @param name transformer name
 * @param extract returns a list of compound names to extract
 * @param blockTransform does something with the supplied compound time matrix to produce a value
 * @return [TransformerFn]
 */
private fun transformerFactory(
    name: String,
    extract: (String, Set<CompoundBase>) -> Collection<CompoundBase>,
    blockTransform: (Map<CompoundBase, List<Double>>) -> Double
): TransformerFn =
    { allVals, spec, start, duration, freq ->

        val freqIter = freq.circularIterator()

        var blockIndex = 0
        val outputs: MutableList<RangeValue<Double, Int>> = mutableListOf()

        val extractNames = extract(spec, allVals.keys)

        val window = mutableMapOf<CompoundBase, List<Double>>()

        block@ while (blockIndex <= duration) {
            val blockLast = freqIter.next()
            val blockStartIndex = start + blockIndex
            val blockEndIndex = blockStartIndex + blockLast

            for (compoundName in extractNames) {
                val valsForName = allVals[compoundName]
                requireNotNull(valsForName) {
                    "compound <$compoundName> not evaluated prior to transformer <$name>"
                }
                if (blockEndIndex > valsForName.size)
                    continue
                window[compoundName] = valsForName.subList(blockStartIndex, blockEndIndex)
            }

            if (window.isEmpty()) {
                require(outputs.isNotEmpty()) {
                    "the transformer $spec:$name is invalid because there is no data for it to transform"
                }
                break
            }
            outputs.add(RangeValue(blockTransform(window), blockStartIndex, blockEndIndex))
            blockIndex += blockLast
            window.clear()
        }

        outputs
    }

/** Returns a map of available transformers to their helps and usages.
 * @see [TransformerInfo]
 */
fun getTransformersInfo(): Map<String, TransformerInfo> =
    transformerEntries.mapValues { (_, v) -> v.info }

internal val transformerEntries: Map<String, TransformerEntry> =
    mapOf(
        "median" to TransformerEntry(
            TransformerInfo("median of dose per use", "<compound>"),
            transformerFactory("median",
                { c, _ -> listOf(CompoundBase(c)) },
                {
                    require(it.size == 1)
                    val v = it.values.first()
                    (v.max() + v.min()) / 2.0
                }
            )
        )
    )

val TRANSFORMER_FREQ_INFO = mapOf(
    FrequencyName(".") to "each timeDuration quantum"
)

internal fun generateTransformerFreqs(c: Config): Map<FrequencyName, FrequencyValue> =
    TRANSFORMER_FREQ_INFO.mapValues { (k, _) ->
        when (k.value) {
            "." -> listOf(c.tickDuration)
            else -> error("unhandled frequency $k")
        }.let(::FrequencyValue)
    }