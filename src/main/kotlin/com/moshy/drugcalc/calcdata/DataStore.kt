package com.moshy.drugcalc.calcdata

import com.moshy.drugcalc.calc.transformers as cTransformers
import com.moshy.containers.CopyOnWriteContainer
import com.moshy.containers.CopyOnWriteHashMap
import kotlin.time.Duration
import kotlinx.serialization.Contextual


/**
 * Store for data.
 *
 * @property compounds map of compounds; each value is a [CompoundInfo]
 * @property blends map of blends; each value is a map of compound name to dose multiplier
 */
class DataStore private constructor(
    initCompounds: CompoundsMap,
    initBlends: Map<String, BlendEntry>,
    initFrequencies: FrequenciesMap
) {
    constructor() : this(emptyMap(), emptyMap(), emptyMap())

    /**
     * Blend entry. [components] used for calculator and [names] used for serialization.
     */
    data class BlendEntry(val components: List<Component>, val names: BlendValue) {
        /**
         * @param doseMultiplier value to multiply dose by to get active dose;
         *                       this avoids looking up and multiplying by [CompoundInfo.pctActive] every time
         * @param cInfo compound info; we need this for [CompoundInfo.halfLife] and [CompoundInfo.activeCompound]
         */
        data class Component(val doseMultiplier: Double, val cInfo: CompoundInfo)
    }

    internal val compounds: CompoundsMap = CopyOnWriteHashMap(initCompounds)
    internal val blends: Map<String, BlendEntry> = CopyOnWriteHashMap(initBlends)

    /*
     * private because value of values["timeStep"] can change between frequency add and frequency fetch;
     * use getFrequency()/getFrequencies
     */
    val frequencies: FrequenciesMap = CopyOnWriteHashMap(initFrequencies)

    // cache of compound bases; used in getTransformer
    private var compoundBases: Set<String>? = null
        get() {
            if (field == null)
                field = compounds.values.map { it.activeCompound }.toSet()
            return field
        }

    /**
     * Add a Map of Compounds to the session data store.
     * ':' cannot be a character in a compound name and any name cannot be already in use for a blend.
     * @see CompoundInfo
     * @throws IllegalArgumentException on constraint failure
     */
    fun addCompounds(compounds: Map<String, CompoundInfo>) {
        checkNames("compounds", forbiddenChars = ":")(this.compounds.keys, compounds.keys)
        compounds.keys.intersect(blends.keys).firstOrNull()?.let {
            throw IllegalArgumentException("\"$it\" already exists in blends")
        }
        for ((name, info) in compounds.entries) {
            require (info.activeCompound.isNotEmpty()) {
                "compounds: \"$name\" has empty activeCompound"
            }
        }
        this.compounds as CopyOnWriteHashMap
        this.compounds.write {
            putAll(compounds)
        }
        compoundBases = null
    }

    /**
     * Add a Map of Blends to the session data store. Name constraints are analogous to [addCompounds].
     *
     * Each valid BlendValue has the following constraints:
     * (1) must have at least two components,
     * (2) each key must be a valid compound name, and
     * (3) each value is positive
     * @throws IllegalArgumentException on constraint failure
     */
    fun addBlends(blends: Map<String, BlendValue>) {
        checkNames("blends", forbiddenChars = ":")(this.blends.keys, blends.keys)
        blends.keys.intersect(compounds.keys).firstOrNull()?.let {
            throw IllegalArgumentException("\"$it\" already exists in compounds")
        }
        val toAdd =
            blends.mapValues { (name, components) ->
                require(components.size > 1) {
                    "blend \"$name\": 2 or more components necessary for blend"
                }
                val totalDose = components.values.sum()
                val blendVals = components.map { (compound, dose) ->
                    require(dose > 0.0) {
                        "blend \"$name\", component \"$compound\": zero dose"
                    }
                    val cInfo = requireNotNull(compounds[compound]) {
                        "blend \"$name\": unrecognized component \"$compound\""
                    }
                    BlendEntry.Component(dose * cInfo.pctActive / totalDose, cInfo)
                }
                BlendEntry(blendVals, components)
            }
        this.blends as CopyOnWriteHashMap
        this.blends.write {
            putAll(toAdd)
        }
    }

    /**
     * Add a Map of Frequencies to the session data store.
     *
     * Each valid FrequencyValue contains only positive numbers.
     * @throws IllegalArgumentException on constraint failure
     */
    fun addFrequencies(frequencies: Map<String, FrequencyValue>) {
        checkNames("frequencies")(this.frequencies.keys, frequencies.keys)
        for ((name, frequencyValues) in frequencies) {
            require(frequencyValues.isNotEmpty()) {
                "frequency \"$name\": empty list"
            }
            for ((index, freqVal) in frequencyValues.withIndex()) {
                require(freqVal.isPositive()) {
                    "frequency \"$name\": value $freqVal@[$index]"
                }
            }
        }

        this.frequencies as CopyOnWriteHashMap
        this.frequencies.write {
            putAll(frequencies)
        }
    }

    // extract transformer-spec to (base, transformer-name) tuple
    internal fun getTransformer(compound: String): Pair<String, String>? {
        val parts =
            compound
                .split(":", limit = 2)
                .mapNotNull {
                    it
                        .lowercase()
                        .takeIf { _ -> it.isNotEmpty() }
                }
        // this should only fail if the custom getter doesn't trigger
        assert(compoundBases != null)
        if (parts.size != 2)
            return null
        if (parts[0] !in compoundBases!!)
            return null
        return Pair(parts[0], parts[1]).takeIf { it.second in cTransformers }
    }

    /**
     * Get all blends, in a format usable for reading.
     */
    fun getBlends() = blends.mapValues { (_, e) -> e.names }

    /**
     * Remove compounds.
     *
     * Will fail if any compound name is missing or any blend depends on any of the compounds.
     */
    fun removeCompounds(names: Collection<String>) {
        compounds.missingKeyOrNull(names)?.let {
            throw IllegalArgumentException("missing compound \"$it\"")
        }
        for (name in names) {
            if (name in compounds) {
                for ((bName, bValue) in blends) {
                    require(name !in bValue.names.keys) {
                        "deleting compound \"$name\" would orphan blend \"$bName\""
                    }
                }
            }
        }
        this.compounds as CopyOnWriteHashMap
        this.compounds.write {
            keys.removeAll(names)
        }
    }

    /**
     * Remove blends.
     *
     * Will fail if any of the blend names are missing.
     *
     */
    fun removeBlends(names: Collection<String>) {
        blends.missingKeyOrNull(names)?.let {
            throw IllegalArgumentException("missing blend \"$it\"")
        }
        this.blends as CopyOnWriteHashMap
        this.blends.write {
            keys.removeAll(names)
        }
    }

    /**
     * Remove frequencies.
     *
     * Will fail if any of the frequency names are missing.
     */
    fun removeFrequencies(names: Collection<String>) {
        frequencies.missingKeyOrNull(names)?.let {
            throw IllegalArgumentException("missing frequency \"$it\"")
        }
        this.frequencies as CopyOnWriteHashMap
        this.frequencies.write {
            keys.removeAll(names)
        }
    }

    /**
     * Create a copy of this instance. Supply a lambda whose receiver is a DataStore for modifications.
     */
    fun buildAndFreezeCopy(modify: DataStore.() -> Unit) =
        DataStore(compounds, blends, frequencies).apply {
            modify()
            freeze()
        }
    /** Create a copy without modifying or freezing. */
    fun buildCopy() = DataStore(compounds, blends, frequencies)

    /** Freeze the containers inside this instance. */
    fun freeze() {
        for (c in listOf(compounds, blends, frequencies)) {
            c as CopyOnWriteContainer<*, *>
            c.freeze()
        }
    }


    override fun hashCode(): Int {
        var result = compounds.hashCode()
        result = 31 * result + blends.hashCode()
        result = 31 * result + frequencies.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataStore) return false

        if (compounds != other.compounds) return false
        if (blends != other.blends) return false
        if (frequencies != other.frequencies) return false

        return true
    }

    fun isEmpty() = listOf(compounds, blends, frequencies).all { it.isEmpty() }

}

/**
 * Returns a closure that then checks if a key name is valid for subsequent insertion.
 * This closure raises [IllegalArgumentException] on failure.
 *
 * @param container container name (for exception)
 * @param forbiddenChars set of forbidden characters
 * @return a function taking two parameters, (existing: Set<String>, new: Set<String>)
 */
private fun checkNames(container: String, forbiddenChars: String = "") =
    @Throws(IllegalArgumentException::class)
    { existing: Set<String>, new: Set<String> ->
        for (key in new) {
            for (ch in forbiddenChars) {
                if (ch in key) {
                    require(false) { "$container: forbidden character: '$ch'" }
                }
            }
            require(key.isNotEmpty()) { "$container: empty key" }
        }
        existing.intersect(new).firstOrNull()?.let {
            throw IllegalArgumentException("$container: \"$it\" already exists")
        }
    }

private fun <K, V> Map<K, V>.missingKeyOrNull(keys: Collection<K>) =
    keys.firstOrNull { it !in this.keys }

internal typealias BlendValue = Map<String, Double>
internal typealias FrequencyValue = List<@Contextual Duration>

internal typealias CompoundsMap = Map<String, CompoundInfo>
internal typealias BlendsMap = Map<String, BlendValue>
internal typealias FrequenciesMap = Map<String, FrequencyValue>