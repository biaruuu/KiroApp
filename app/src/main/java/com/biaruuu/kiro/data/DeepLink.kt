package com.biaruuu.kiro.data

import kotlinx.coroutines.flow.MutableStateFlow

/** A page to open as soon as the app is on screen (from a notification), such as "d:123" for an anime. */
object DeepLink {
    val request = MutableStateFlow<String?>(null)
}
