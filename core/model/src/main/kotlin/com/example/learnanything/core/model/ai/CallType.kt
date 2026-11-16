package com.example.learnanything.core.model.ai

/**
 * How strong a model a call needs. The router uses this to pick a provider.
 * STRONG calls never run on an on-device model.
 */
enum class Tier { SMALL, MEDIUM, STRONG }

/** Every AI call the app makes. Each has a versioned prompt and a JSON schema. */
enum class CallType(val tier: Tier) {
    CLARIFY(Tier.MEDIUM),
    OUTLINE(Tier.STRONG),
    LESSON(Tier.MEDIUM),
    QUIZ(Tier.MEDIUM),
    PRACTICAL(Tier.STRONG),
    GRADE(Tier.STRONG),
    RESCUE(Tier.SMALL),
    HINT(Tier.SMALL),
    RESOURCES(Tier.SMALL),
}
