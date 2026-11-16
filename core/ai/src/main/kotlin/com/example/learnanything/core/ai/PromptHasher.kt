package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.PromptSpec
import java.security.MessageDigest

object PromptHasher {
    /** SHA-256 over everything that changes the expected answer. */
    fun hash(spec: PromptSpec): String {
        val material = listOf(
            spec.callType.name,
            "v${spec.version}",
            spec.schema.id,
            spec.system,
            spec.user,
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
