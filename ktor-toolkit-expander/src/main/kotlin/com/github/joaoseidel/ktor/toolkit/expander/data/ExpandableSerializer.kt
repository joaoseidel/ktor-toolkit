package com.github.joaoseidel.ktor.toolkit.expander.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializes an [Expandable] as either its reference string or the object it resolved to.
 *
 * JSON only: both directions work in `JsonElement`, because a field that is a string in one
 * response and an object in the next has no fixed shape to describe. A non-JSON format therefore
 * fails with a `ClassCastException` on the encoder, and the descriptor stays an empty class — it
 * exists to satisfy the interface, not to describe the output.
 *
 * Deserialization is lossy by design: a string comes back as [Expandable.Ref] and an object as
 * [Expandable.Resolved], so an [Expandable.Partial] read back is indistinguishable from an object
 * that was simply small. Nothing round-trips a projection, and a client has no use for the
 * distinction.
 *
 * @param contentSerializer Serializes the resolved value. The kotlinx.serialization plugin supplies
 *   it wherever an `Expandable<T>` appears in a `@Serializable` class.
 */
class ExpandableSerializer<T>(
    private val contentSerializer: KSerializer<T>,
) : KSerializer<Expandable<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Expandable")

    override fun serialize(
        encoder: Encoder,
        value: Expandable<T>,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is Expandable.Ref -> {
                jsonEncoder.encodeJsonElement(JsonPrimitive(value.id))
            }

            is Expandable.Resolved -> {
                jsonEncoder.encodeJsonElement(
                    jsonEncoder.json.encodeToJsonElement(contentSerializer, value.value),
                )
            }

            is Expandable.Partial -> {
                // Projection selects by JSON key, so the encoded value has to have keys. A
                // primitive or a list cannot be projected, and casting says so at the point of use.
                val fullJson = jsonEncoder.json.encodeToJsonElement(contentSerializer, value.value) as JsonObject
                val filtered = JsonObject(fullJson.filterKeys { it.lowercase() in value.fields })
                jsonEncoder.encodeJsonElement(filtered)
            }
        }
    }

    override fun deserialize(decoder: Decoder): Expandable<T> {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                Expandable.Ref(element.content)
            }

            is JsonObject -> {
                Expandable.Resolved(
                    jsonDecoder.json.decodeFromJsonElement(contentSerializer, element),
                )
            }

            else -> {
                error("Expandable cannot be deserialized from ${element::class.simpleName}")
            }
        }
    }
}
