package com.biaruuu.kiro.util

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns low-level exceptions into something a person can read. */
fun Throwable.userMessage(): String = when (this) {
    is UnknownHostException -> "No internet connection. Check your network and try again."
    is SocketTimeoutException -> "The connection timed out. Please try again."
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}
