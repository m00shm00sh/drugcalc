package com.moshy.drugcalc.cmdclient.io

import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.decode
import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.encode

import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = UrlStringSerializer::class)
@JvmInline
internal value class UrlString(val value: String)
internal class UrlStringSerializer() : KSerializer<UrlString> {
    override val descriptor =
        PrimitiveSerialDescriptor("UrlString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UrlString) =
        encoder.encodeString(value.value.encode())

    override fun deserialize(decoder: Decoder): UrlString =
        UrlString(decoder.decodeString().decode())

    companion object {
        fun String.encode() = encodeURLQueryComponent(spaceToPlus = true, encodeFull = true)
        fun String.decode() = decodeURLQueryComponent(plusIsSpace = true)
    }
}

@Serializable(with = UrlStringListSerializer::class)
@JvmInline
internal value class UrlStringList(val value: List<String>)

internal object UrlStringListSerializer : KSerializer<UrlStringList> {
    override val descriptor =
        PrimitiveSerialDescriptor("dc.p.UrlStringList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UrlStringList) =
        value.value
            .joinToString(",") {
                it.encode()
            }
            .let(encoder::encodeString)


    override fun deserialize(decoder: Decoder): UrlStringList =
            decoder.decodeString()
                .split(",")
                .map { it.decode() }
                .let(::UrlStringList)
}
