package com.moshy.drugcalc.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.time.Duration

private object LenientDurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlin.time.Duration\$Flexible", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Duration =
        Duration.parse(decoder.decodeString())
    // lenient in accepting, strict in generating
    override fun serialize(encoder: Encoder, value: Duration) =
        encoder.encodeString(value.toIsoString())
}
private object StrictDurationSerializer: KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlin.time.Duration\$Flexible", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Duration =
        Duration.parseIsoString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Duration) =
        encoder.encodeString(value.toIsoString())
}

internal val WithStrictIsoDuration = SerializersModule {
    contextual(Duration.serializer())
}
internal val WithLenientDuration = SerializersModule {
    contextual(LenientDurationSerializer)
}

internal val JsonWithStrictIsoDuration = Json { serializersModule = WithStrictIsoDuration }
internal val JsonWithLenientIsoDuration = Json { serializersModule = WithLenientDuration }