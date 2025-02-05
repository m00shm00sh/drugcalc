package com.moshy.drugcalc.internaltypes

/**
 * Represents a range value, nominally aggregates that transform a subsequence to a single value.
 *
 * The duration endpoints are index-ish, so, among other things, both Double and Int can be used, but the primary
 * constraint is implementing Comparable.
 *
 * @property value the value
 * @property from start index-ish
 * @property until end index-ish
 */
internal data class RangeValue<DataT, IndexishT: Comparable<IndexishT>>
    (val value: DataT, val from: IndexishT, val until: IndexishT)
{
    init {
        require(until > from) { "invalid range: from=$from to=$until" }
    }
}
internal typealias RangeList<DataT, IndexishT> = List<RangeValue<DataT, IndexishT>>
