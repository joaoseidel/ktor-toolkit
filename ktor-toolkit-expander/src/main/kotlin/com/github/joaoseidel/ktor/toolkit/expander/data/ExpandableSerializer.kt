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
