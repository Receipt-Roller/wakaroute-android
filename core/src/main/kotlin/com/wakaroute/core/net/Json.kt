package com.wakaroute.core.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The decoder used for every response.
 *
 * `ignoreUnknownKeys` is not laziness: the catalogue publishes structure ahead
 * of data and gains fields between releases, and an app already on a student's
 * phone must keep working when that happens.
 */
val WakaRouteJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Reads a number that the backend sometimes sends as a string.
 *
 * The 開発ガイド §10 records that numeric fields come back as `"12"` on some
 * endpoints. Declaring them plainly as `Int` makes decoding fail for the whole
 * response — one quoted number would empty a student's school search.
 */
object LenientInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val primitive = decoder.asJsonPrimitive() ?: return decoder.decodeInt()
        return primitive.content.trim().toIntOrNull()
            ?: primitive.content.trim().toDoubleOrNull()?.toInt()
            ?: throw SerializationException("Not a number: ${primitive.content}")
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** As [LenientInt], for fractional values such as 偏差値 and coordinates. */
object LenientDouble : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val primitive = decoder.asJsonPrimitive() ?: return decoder.decodeDouble()
        return primitive.content.trim().toDoubleOrNull()
            ?: throw SerializationException("Not a number: ${primitive.content}")
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

private fun Decoder.asJsonPrimitive(): JsonPrimitive? =
    (this as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive
