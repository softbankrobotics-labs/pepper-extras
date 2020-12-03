package com.softbankrobotics.dx.pepperextras.util

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
How coroutines works:
  - CoroutineScope vs CoroutineContext
    https://medium.com/@elizarov/coroutine-context-and-scope-c8b255d59055
  - https://medium.com/swlh/everything-you-need-to-know-about-kotlin-coroutines-b3d94f2bc982
 */

object SingleThread {
    val Dispatcher = Executors.newSingleThreadExecutor {
        Thread(it, "PepperExtrasSingleThread")
    }.asCoroutineDispatcher()

    // Singleton scope that do does not kill all its children.
    val GlobalScope = CoroutineScope(SupervisorJob() + Dispatcher)

    // Construct a new CoroutineScope that kills its children
    fun newCoroutineScope(): CoroutineScope = CoroutineScope(Dispatcher)
}


private val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
private const val STRING_LENGTH = 6
private fun getRandomString(): String {
    val random = SecureRandom()
    val bytes = ByteArray(STRING_LENGTH)
    random.nextBytes(bytes)
    return (bytes.indices)
        .map {
            charPool[random.nextInt(charPool.size)]
        }.joinToString("")
}

internal val LazyCoroutineFutureToDeferredMap = mutableMapOf<Future<*>, Deferred<*>>()

fun <T> CoroutineScope.asyncFuture(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T): Future<T> {
    var name = ""
    val promise = Promise<T>()
    val future = promise.future
    val deferred = async(context, start) {
        name = coroutineContext[CoroutineName.Key]?.name ?: getRandomString()
        Log.d("asyncFuture", "Coroutine(\"${name}\") started.")
        val value = coroutineScope(block)
        Log.d("asyncFuture", "Coroutine(\"${name}\") ended value: $value")
        promise.setValue(value)
    }.apply {
        invokeOnCompletion { cause ->
            LazyCoroutineFutureToDeferredMap.remove(future)
            if (cause != null)
                Log.d("asyncFuture", "Coroutine(\"${name}\") ended with exception: $cause")
            if (cause is CancellationException)
                promise.setCancelled()
            else if (cause != null) {
                if (cause.message != null)
                    promise.setError(cause.message)
                else
                    promise.setError(cause.toString())
            }
        }
    } .also {
        if (start == CoroutineStart.LAZY) { LazyCoroutineFutureToDeferredMap[future] = it }
    }
    promise.setOnCancel {
        GlobalScope.launch {
            Log.d("asyncFuture", "Coroutine(\"${name}\") is getting cancelled since future is cancelled.")
            deferred.cancel()
        }
    }
    return future
}
