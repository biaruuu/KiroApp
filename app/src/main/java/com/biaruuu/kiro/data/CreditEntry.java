package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

/** One staff member and the role they had, e.g. ("Director", "Tomohiko Ito"). */
public final class CreditEntry {

    private final String role;
    private final String name;

    public CreditEntry(@NonNull String role, @NonNull String name) {
        this.role = role;
        this.name = name;
    }

    @NonNull
    public String getRole() {
        return role;
    }

    @NonNull
    public String getName() {
        return name;
    }
}
