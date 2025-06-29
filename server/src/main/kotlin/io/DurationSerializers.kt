package com.moshy.drugcalc.server.io

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.*
import kotlin.time.Duration

private object LenientDurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlin.time.Duration\$Flexible", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration =
        Duration.parse(decoder.decodeString())

    // lenient in accepting, strict in generating
    override fun serialize(encoder: Encoder, value: Duration) =
        encoder.encodeString(value.toIsoString())
}

internal val WithLenientDuration = SerializersModule {
    contextual(LenientDurationSerializer)
}

internal val JsonWithLenientIsoDuration = Json {
    serializersModule = WithLenientDuration
}