package com.plumemu.home

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

private val NO_TINTS: State<Map<String, Color>> = mutableStateOf(emptyMap())

/** Each system's own colour by its short code in capitals ([systemTintKey]); empty until the platform data is read, then a missing cover takes the accent. */
internal val LocalSystemTints = compositionLocalOf { NO_TINTS }
