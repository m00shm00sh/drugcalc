package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.common.assert

/*
 * Reducers for multiple compounds. Used for CycleKt.evaluateDecodedCycle
 *
 * NOTE: OffsetList and reducer are only used by functions inside Cycle.kt so merge the files here.
 *       Nothing is stopping a Transformer from using it, but it has no place outside calc.
 */
/**
 * Represents a list with an offset.
 * @property offset starting offset
 * @property db list of data
 */
internal data class OffsetList<T>(val offset: Int, val data: List<T>) {
    init {
        require(offset >= 0) { "negative offset" }
        require(data.isNotEmpty()) { "empty list" }
    }
}

internal fun <T> List<T>.toOffsetList(offset: Int): OffsetList<T> = OffsetList(offset, this)

/**
 * Reduce a list of offset-aware lists to a common list via element-wise addition.
 *
 * @param values the list of values to offset-aware reduce
 * @param sizeHint size hint
 * @return appropriately reduced [values]
 */
internal fun reducer(values: List<OffsetList<Double>>, sizeHint: Int = 0): List<Double> {
    assert(
        { sizeHint == 0 || sizeHint == values.maxOf { it.offset + it.data.size } },
        { "sizeHint has unexpected size" }
    )
    val maxLength = sizeHint.takeIf { it > 0 } ?: values.maxOf { it.offset + it.data.size }
    val outList = MutableList(maxLength) { 0.0 }
    for (item in values) {
        val offset = item.offset
        val data = item.data
        for (i in data.indices) {
            outList[offset + i] += data[i]
        }
    }
    return outList
}
