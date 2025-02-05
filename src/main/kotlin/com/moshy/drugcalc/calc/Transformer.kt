package com.moshy.drugcalc.calc

import com.moshy.drugcalc.internaltypes.RangeValue
import com.moshy.containers.circularIterator

/** Returns a [TransformerFn] that applies [blockTransform] to each block. */
private fun transformerFactory(blockTransform: (@TimeTickIndexed List<Double>, @TimeTickScaled Int) -> Double) =
    fun (
        vals: @TimeTickIndexed List<Double>,
        start: @Positive @TimeTickScaled Int,
        duration: @Positive @TimeTickScaled Int,
        freq: List<@Positive @TimeTickScaled Int>
    ): List<RangeValue<Double, @TimeTickScaled Int>> {

        val freqIter = freq.circularIterator()

        var blockIndex = 0
        val outputs: MutableList<RangeValue<Double, @TimeTickScaled Int>> = mutableListOf()

        while (blockIndex <= duration) {
            val blockLast = freqIter.next()
            val blockStartIndex = start + blockIndex
            val blockEndIndex = blockStartIndex + blockLast
            if (blockEndIndex > vals.size)
                break
            val blockVals = vals.subList(blockStartIndex, blockEndIndex)
            outputs.add(RangeValue(blockTransform(blockVals, blockStartIndex), blockStartIndex, blockEndIndex))
            blockIndex += blockLast
        }
        return outputs
    }


private typealias TransformerFn =
    (   @TimeTickIndexed List<Double> /* vals */,
        @Positive @TimeTickScaled Int /* start */,
        @Positive @TimeTickScaled Int /* duration */,
        List<@Positive @TimeTickScaled Int> /* freqs */
    ) -> List<RangeValue<Double, @TimeTickScaled Int>>
internal val transformers =
    mapOf(
    "median" to Pair("median of dose per injection", transformerFactory { it, _ -> (it.max() + it.min()) / 2.0 })
).toMap()
