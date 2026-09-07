package com.riftcompanion.app.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull as JsonNullElement

@Serializable(with = JsonValueSerializer::class)
sealed class JsonValue {
    abstract val searchText: String

    data class Str(val value: String) : JsonValue() {
        override val searchText get() = value
    }
    data class Num(val value: Double) : JsonValue() {
        override val searchText get() = value.toString()
    }
    data class Bool(val value: Boolean) : JsonValue() {
        override val searchText get() = if (value) "true" else "false"
    }
    data class Obj(val value: Map<String, JsonValue>) : JsonValue() {
        override val searchText get() = value.values.joinToString(" ") { it.searchText }
    }
    data class Arr(val value: List<JsonValue>) : JsonValue() {
        override val searchText get() = value.joinToString(" ") { it.searchText }
    }
    data object Null : JsonValue() {
        override val searchText get() = ""
    }
}

object JsonValueSerializer : KSerializer<JsonValue> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonValue) {
        encoder.encodeSerializableValue(JsonElement.serializer(), value.toJsonElement())
    }

    override fun deserialize(decoder: Decoder): JsonValue {
        return decoder.decodeSerializableValue(JsonElement.serializer()).toJsonValue()
    }
}

private fun JsonValue.toJsonElement(): JsonElement = when (this) {
    is JsonValue.Str -> JsonPrimitive(value)
    is JsonValue.Num -> JsonPrimitive(value)
    is JsonValue.Bool -> JsonPrimitive(value)
    is JsonValue.Obj -> JsonObject(value.mapValues { it.value.toJsonElement() })
    is JsonValue.Arr -> JsonArray(value.map { it.toJsonElement() })
    JsonValue.Null -> JsonNullElement
}

// Public extension to convert JsonElement to JsonValue (for use in DtoMapper)
fun JsonElement.toJsonValue(): JsonValue = when (this) {
    is JsonPrimitive -> {
        if (isString) JsonValue.Str(content)
        else content.toBooleanStrictOrNull()?.let { JsonValue.Bool(it) }
            ?: content.toDoubleOrNull()?.let { JsonValue.Num(it) }
            ?: JsonValue.Str(content)
    }
    is JsonObject -> JsonValue.Obj(entries.associate { it.key to it.value.toJsonValue() })
    is JsonArray -> JsonValue.Arr(map { it.toJsonValue() })
    JsonNullElement -> JsonValue.Null
}

// Helper to extract display values from attributes
fun Map<String, JsonValue>.firstText(keys: List<String>): String? {
    for (key in keys) {
        val value = this[key] ?: continue
        when (value) {
            is JsonValue.Str -> {
                if (value.value.isNotBlank()) return value.value
            }
            is JsonValue.Arr -> {
                val lines = value.value.mapNotNull {
                    (it as? JsonValue.Str)?.value
                }
                if (lines.isNotEmpty()) return lines.joinToString("\n")
            }
            else -> continue
        }
    }
    return null
}

fun Map<String, JsonValue>.firstDisplayValue(keys: List<String>): String? {
    for (key in keys) {
        val value = this[key] ?: continue
        when (value) {
            is JsonValue.Str -> return value.value
            is JsonValue.Num -> return if (value.value == value.value.toLong().toDouble())
                value.value.toLong().toString() else value.value.toString()
            is JsonValue.Bool -> return if (value.value) "Yes" else "No"
            else -> continue
        }
    }
    return null
}
