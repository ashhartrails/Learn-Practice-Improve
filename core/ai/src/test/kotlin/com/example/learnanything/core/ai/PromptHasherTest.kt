package com.example.learnanything.core.ai

import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.outline.OutlinePrompt
import com.example.learnanything.core.model.outline.OutlineRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PromptHasherTest {
    @Test fun sameInputSameHash() {
        val a = OutlinePrompt.build(OutlineRequest("SQL", Level.BEGINNER, 3))
        val b = OutlinePrompt.build(OutlineRequest("SQL", Level.BEGINNER, 3))
        assertThat(PromptHasher.hash(a)).isEqualTo(PromptHasher.hash(b))
        assertThat(PromptHasher.hash(a)).hasLength(64)
    }

    @Test fun differentInputDifferentHash() {
        val a = OutlinePrompt.build(OutlineRequest("SQL", Level.BEGINNER, 3))
        val b = OutlinePrompt.build(OutlineRequest("SQL", Level.ADVANCED, 3))
        assertThat(PromptHasher.hash(a)).isNotEqualTo(PromptHasher.hash(b))
    }
}
