package com.example.learnanything.core.ai.validation

import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.SchemaType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement

/** Validates a JSON value against a JSON Schema. Returns problems; empty means valid. */
interface SchemaValidator {
    fun validate(schemaJson: String, instance: JsonElement): List<String>
}

/** Draft 2020-12 validator. Enforces every keyword, including ones Gemini ignores (e.g. minLength). */
@Singleton
class JsonSchemaValidator @Inject constructor() : SchemaValidator {
    private val compiled = ConcurrentHashMap<String, JsonSchema>()

    override fun validate(schemaJson: String, instance: JsonElement): List<String> {
        val schema = compiled.getOrPut(schemaJson) {
            JsonSchema.fromDefinition(schemaJson, SchemaType.DRAFT_2020_12)
        }
        val errors = mutableListOf<String>()
        schema.validate(instance, ErrorCollector { error ->
            val path = error.objectPath.toString().ifEmpty { "/" }
            errors += "$path: ${error.message}"
        })
        return errors
    }
}
