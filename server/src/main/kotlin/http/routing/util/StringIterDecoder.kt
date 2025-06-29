package com.moshy.drugcalc.server.http.routing.util

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*
import java.util.concurrent.ConcurrentHashMap

/** Decodes a list of strings into an object.
 *
 * Restrictions: a class cannot have members that are class, list, map, anonymous object, open class, or polymorphic
 *               type
 */
@OptIn(ExperimentalSerializationApi::class)
internal class StringIterDecoder private constructor(
    private val iter: PeekableIterator<String>,
    private val depth: Int = 0,
    override val serializersModule: SerializersModule
) : AbstractDecoder() {
    constructor(iterable: Iterable<String>, serializersModule: SerializersModule = EmptySerializersModule()) :
            this(PeekableIterator(iterable.iterator()), 0, serializersModule)

    private var index = 0

    private fun nextStr(): String {
        if (!iter.hasNext()) throw SerializationException()
        ++index
        return iter.next()
    }

    override fun decodeNotNullMark(): Boolean = iter.peek()?.isNotEmpty() ?: false
    override fun decodeNull(): Nothing? = null.takeIf { nextStr().isEmpty() }
    override fun decodeBoolean(): Boolean = nextStr().toBoolean()
    override fun decodeByte(): Byte = nextStr().toByte()
    override fun decodeShort(): Short = nextStr().toShort()
    override fun decodeInt(): Int = nextStr().toInt()
    override fun decodeLong(): Long = nextStr().toLong()
    override fun decodeFloat(): Float = nextStr().toFloat()
    override fun decodeDouble(): Double = nextStr().toDouble()
    override fun decodeChar(): Char = nextStr().firstOrNull() ?: throw SerializationException()
    override fun decodeString(): String = nextStr()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = enumDescriptor.elementNames.indexOf(iter.next())
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (depth > 0 && !checked.getOrPut(descriptor) {
                for (elem in (0..<descriptor.elementsCount).map(descriptor::getElementDescriptor)) {
                    when (elem.kind) {
                        is PolymorphicKind.OPEN, is PolymorphicKind.SEALED,
                        is StructureKind.CLASS, is StructureKind.LIST, is StructureKind.MAP, is StructureKind.OBJECT ->
                            return@getOrPut false
                        // PrimitiveKind.*, SerialKind.CONTEXTUAL, SerialKind.ENUM
                        else -> {}
                    }
                }
                true
            })
            throw SerializationException("unsupported recursive structure")
        return StringIterDecoder(iter, depth + 1, serializersModule)
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        try {
            while (iter.peek().isNullOrEmpty()) {
                if (index == descriptor.elementsCount)
                    return CompositeDecoder.DECODE_DONE
                when (descriptor.getElementDescriptor(index).kind) {
                    is StructureKind.CLASS,
                    is StructureKind.LIST, is StructureKind.MAP ->
                        throw SerializationException("refusing to handle recursive member")

                    is StructureKind.OBJECT ->
                        throw SerializationException("refusing to handle object")

                    is PolymorphicKind.OPEN, PolymorphicKind.SEALED ->
                        throw SerializationException("refusing to handle polymorphic")
                    // PrimitiveKind.*, SerialKind.CONTEXTUAL, SerialKind.ENUM
                    else -> {}
                }
                nextStr()
            }
        } catch (_: NoSuchElementException) {
            return CompositeDecoder.DECODE_DONE
        }
        return index
    }

    fun <U> decodeTo(deserializer: DeserializationStrategy<U>): U =
        decodeSerializableValue(deserializer)

    inline fun <reified U> decodeTo(): U = decodeTo(serializersModule.serializer<U>())

    companion object {
        val checked = ConcurrentHashMap<SerialDescriptor, Boolean>()
    }
}

private class PeekableIterator<T>(
    private val itr: Iterator<T>,
) : Iterator<T> {
    private var peeked: T? = null

    override fun hasNext(): Boolean {
        peeked?.let { return true }
        return itr.hasNext()
    }

    override fun next(): T {
        val oldPeeked = peeked
        if (oldPeeked == null) return itr.next()
        peeked = null
        return oldPeeked
    }

    fun peek(): T? {
        val oldPeeked = peeked
        oldPeeked?.let { return it }
        if (itr.hasNext()) {
            val next = itr.next()
            peeked = next
            return next
        }
        return null
    }
}

