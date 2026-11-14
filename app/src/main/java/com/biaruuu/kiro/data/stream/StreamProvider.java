package com.biaruuu.kiro.data.stream;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * A place that can hand out playable video addresses. Providers run in parallel and block, so
 * resolve() may do network work. Add a provider by implementing this and listing it in
 * {@link StreamResolver}.
 */
public interface StreamProvider {

    @NonNull
    String getName();

    @NonNull
    List<StreamSource> resolve(@NonNull StreamRequest request) throws Exception;
}
