package com.example.learnanything.core.model

import kotlinx.serialization.json.Json

/** JSON settings for anything the app stores (payloads, answers). Writes every field explicitly. */
val ModelJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
