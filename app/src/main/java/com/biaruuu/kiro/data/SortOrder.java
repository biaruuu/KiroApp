package com.biaruuu.kiro.data;

/** How search results are ordered. */
public enum SortOrder {
    /** Most popular first (best text match first when a search phrase is typed). */
    POPULARITY,
    /** Newest releases first, ignoring titles that have not been released yet. */
    RECENT
}
