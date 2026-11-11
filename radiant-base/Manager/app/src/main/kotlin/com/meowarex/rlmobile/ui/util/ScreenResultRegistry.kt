package com.meowarex.rlmobile.ui.util

import android.os.Parcelable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// TODO: migrate to androidx-nav v3 since it has built-in screen result support
/**
 * Global result registry for managing screen results across the app.
 * Uses ConcurrentHashMap for synchronous result delivery so that screen dismissals/disposals
 * complete their deferred listeners reliably without depending on a coroutine scope.
 */
internal object ScreenResultRegistry {
    private val results = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    fun <R> registerResult(key: String): CompletableDeferred<R> {
        val deferred = CompletableDeferred<Any?>()
        results[key] = deferred
        @Suppress("UNCHECKED_CAST")
        return deferred as CompletableDeferred<R>
    }

    fun setResult(key: String, result: Any?) {
        results.remove(key)?.complete(result)
    }

    fun clear(key: String) {
        results.remove(key)?.cancel()
    }
}

/**
 * Base class for screens that can return results
 */
abstract class ScreenWithResult<R> : Screen {
    val resultKey: ScreenResultKey = ScreenResultKey()

    /**
     * This sets the result for this screen and completes any listeners.
     */
    fun setResult(value: R) {
        ScreenResultRegistry.setResult(resultKey.key, value)
    }
}

/**
 * Base class for screen models that can set a result for their parent screen.
 */
abstract class ScreenModelWithResult<R>(
    private val resultKey: ScreenResultKey,
) : ScreenModel {
    /**
     * This sets the result for this screen and completes any listeners.
     * Can be safely called synchronously in [ScreenModel.onDispose] or custom confirm actions.
     */
    fun setResult(value: R) {
        ScreenResultRegistry.setResult(resultKey.key, value)
    }
}

@Parcelize
@Serializable
data class ScreenResultKey(val key: String = UUID.randomUUID().toString()) : Parcelable

/**
 * Extension function to show a screen and get its result from anywhere
 */
suspend fun <R> Navigator.pushForResult(
    screen: ScreenWithResult<R>,
): R {
    val deferred = ScreenResultRegistry.registerResult<R>(screen.resultKey.key)
    this.push(screen)
    return try {
        deferred.await()
    } finally {
        ScreenResultRegistry.clear(screen.resultKey.key)
    }
}

/**
 * Extension function to show a screen without waiting for the result
 */
fun <R> Navigator.showWithoutWaiting(
    screen: ScreenWithResult<R>,
): ScreenResultKey {
    ScreenResultRegistry.registerResult<R>(screen.resultKey.key)
    this.push(screen)
    return screen.resultKey
}

/**
 * Function to get a result using the key from a previous [showWithoutWaiting] call
 */
suspend fun <R> getScreenResult(key: ScreenResultKey): R {
    val deferred = ScreenResultRegistry.registerResult<R>(key.key)
    return deferred.await()
}
