package com.example.learnanything.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback palette for Android 8 to 11, which have no dynamic colour. Deep teal + warm amber.
private val Teal40 = Color(0xFF00696B)
private val Teal80 = Color(0xFF4CDADB)
private val Amber40 = Color(0xFF7A5900)
private val Amber80 = Color(0xFFF8BD2A)
private val Slate40 = Color(0xFF4A6364)
private val Slate80 = Color(0xFFB1CBCC)

internal val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF7F8),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Slate40,
    tertiary = Amber40,
    tertiaryContainer = Color(0xFFFFDEA4),
    background = Color(0xFFF9FAFA),
    surface = Color(0xFFF9FAFA),
)

internal val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F50),
    onPrimaryContainer = Color(0xFF6FF7F8),
    secondary = Slate80,
    tertiary = Amber80,
    tertiaryContainer = Color(0xFF5C4300),
)
