package com.masselis.tpmsadvanced.core.ui

import androidx.compose.ui.graphics.Color

// Apart from Color.kt, whose colours read the app's context: a screenshot test has none

/** A warning below the theme's error: a suspended scan, an amber alert */
@Suppress("MagicNumber")
public val Orange: Color = Color(0xFFFF9800)

/** Neither good nor bad: Lite scanning all the time, without a trigger to start it */
@Suppress("MagicNumber")
public val Blue: Color = Color(0xFF2196F3)
