package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Turns low-level exceptions into messages a person can read. */
public final class ApiErrors {

    private ApiErrors() {
    }

    @NonNull
    public static String userMessage(@NonNull Throwable t) {
        if (t instanceof UnknownHostException) {
            return "No internet connection. Check your network and try again.";
        }
        if (t instanceof SocketTimeoutException) {
            return "The connection timed out. Please try again.";
        }
        String message = t.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return "Something went wrong. Please try again.";
    }
}
