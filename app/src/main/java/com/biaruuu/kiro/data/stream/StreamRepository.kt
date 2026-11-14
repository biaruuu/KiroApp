package com.biaruuu.kiro.data.stream

import android.content.Context
import com.biaruuu.kiro.data.AniListApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine wrapper over the Java [StreamResolver]. */
object StreamRepository {
    suspend fun resolve(context: Context, request: StreamRequest): List<StreamSource> =
        suspendCancellableCoroutine { cont ->
            val handle = StreamResolver.resolve(
                context,
                request,
                object : AniListApi.Callback<List<StreamSource>> {
                    override fun onSuccess(result: List<StreamSource>) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onError(error: Exception) {
                        if (cont.isActive) cont.resumeWithException(error)
                    }
                },
            )
            cont.invokeOnCancellation { handle.cancel() }
        }
}
