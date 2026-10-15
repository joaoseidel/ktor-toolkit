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
