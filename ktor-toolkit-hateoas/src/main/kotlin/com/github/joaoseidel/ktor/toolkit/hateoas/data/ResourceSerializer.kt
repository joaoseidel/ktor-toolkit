package com.github.joaoseidel.ktor.toolkit.hateoas.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

internal const val LINKS_ELEMENT: String = "_links"

internal class ResourceSerializer<T>(
    private val contentSerializer: KSerializer<T>,
) : KSerializer<Resource<T>> {
    override val descriptor: SerialDescriptor = resourceDescriptor(contentSerializer.descriptor)

    override fun serialize(
        encoder: Encoder,
        value: Resource<T>,
    ) {
        val jsonEncoder = encoder as JsonEncoder

        val contentJson = jsonEncoder.json.encodeToJsonElement(contentSerializer, value.content).jsonObject
        val linksJson = jsonEncoder.json.encodeToJsonElement(ListSerializer(Link.serializer()), value.links)
        val finalJson =
            buildJsonObject {
                contentJson.forEach { (key, value) -> put(key, value) }
                put(LINKS_ELEMENT, linksJson)
            }

        jsonEncoder.encodeJsonElement(finalJson)
    }

    override fun deserialize(decoder: Decoder): Resource<T> {
        val jsonDecoder = decoder as JsonDecoder

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val linksJson = jsonObject[LINKS_ELEMENT]
        val links =
            if (linksJson == null) {
                emptyList()
            } else {
                jsonDecoder.json.decodeFromJsonElement(ListSerializer(Link.serializer()), linksJson)
            }

        val contentJson = JsonObject(jsonObject.toMutableMap().apply { remove(LINKS_ELEMENT) })
        val content = jsonDecoder.json.decodeFromJsonElement(contentSerializer, contentJson)

        return Resource(content, links)
    }
}

/**
 * Describes the shape [ResourceSerializer] actually writes: the content's own fields, then `_links`.
 *
 * The flattening happens while encoding, so a descriptor that named `_links` alone would be accurate
 * about the wrapper and silent about everything else in the body. Nothing reads this to serialize —
 * `serialize` and `deserialize` both work in `JsonObject` directly — but anything that inspects the
 * type rather than an instance reads it and nothing else, and OpenAPI schema inference is exactly
 * that. Left naming only `_links`, it documents every wrapped response as a body carrying links and
 * no content.
 *
 * The serial name carries [content]'s, because a schema generator keys components by it and every
 * `Resource<T>` sharing one name collides into whichever was registered first.
 *
 * Only a class contributes elements. [ResourceSerializer] casts the encoded content to a
 * `JsonObject`, so a resource wrapping a list or a primitive fails at encode time regardless; this
 * mirrors that rather than describing a body that cannot be produced. A content field already called
 * `_links` is dropped for the same reason: the wrapper's own value overwrites it when encoding, so
 * declaring it twice would describe a field the client never receives — and duplicate element names
 * fail the descriptor builder outright.
 */
private fun resourceDescriptor(content: SerialDescriptor): SerialDescriptor =
    buildClassSerialDescriptor("Resource<${content.serialName}>") {
        if (content.kind == StructureKind.CLASS) {
            for (index in 0 until content.elementsCount) {
                val name = content.getElementName(index)
                if (name == LINKS_ELEMENT) continue

                element(
                    elementName = name,
                    descriptor = content.getElementDescriptor(index),
                    annotations = content.getElementAnnotations(index),
                    isOptional = content.isElementOptional(index),
                )
            }
        }

        element(LINKS_ELEMENT, ListSerializer(Link.serializer()).descriptor)
    }
