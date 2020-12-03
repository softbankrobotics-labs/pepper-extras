package com.softbankrobotics.dx.pepperextras

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aldebaran.qi.Promise
import com.softbankrobotics.dx.pepperextras.util.await
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CancellationException


@RunWith(AndroidJUnit4::class)
class FutureExtTest {

    @Test
    fun testAwaitAFutureAllowToGetItsValue() {
        runBlocking {
            val promise = Promise<Int>()
            promise.setOnCancel { promise.setCancelled()  }
            var value = 0
            val job = launch {
                value = promise.future.await()
            }
            delay(100)
            promise.setValue(42)
            job.join()
            Assert.assertEquals(value, 42)
        }
    }

    @Test
    fun testAwaitACancelledFutureThrowsCancellationException() {
        runBlocking {
            val promise = Promise<Void>()
            promise.setOnCancel { promise.setCancelled()  }
            var cancelled = false
            val job = launch {
                try {
                    promise.future.await()
                } catch (e: CancellationException) {
                    cancelled = true
                }
            }
            delay(100)
            promise.future.cancel(true)
            job.join()
            Assert.assertTrue(cancelled)
        }
    }

    @Test
    fun testAwaitAFailedFutureThrowsRuntimeException() {
        runBlocking {
            val promise = Promise<Void>()
            promise.setOnCancel { promise.setCancelled()  }
            var error = false
            val job = launch {
                try {
                    promise.future.await()
                } catch (e: Exception) {
                    error = true
                }
            }
            delay(100)
            promise.setError("Game over")
            job.join()
            Assert.assertTrue(error)
        }
    }

    @Test
    fun testAwaitCanBeCancelledAndThatCancelsTheFuture() {
        runBlocking {
            val promise = Promise<Void>()
            promise.setOnCancel { promise.setCancelled()  }
            var cancelled = false
            val job = launch {
                try {
                    promise.future.await()
                } catch (e: CancellationException) {
                    cancelled = true
                }
            }
            delay(100)
            job.cancelAndJoin()
            Assert.assertTrue(cancelled)
            Assert.assertTrue(promise.future.isCancelled)
        }
    }

    @Test
    fun testAwaitCanBeTimeoutedAndThatCancelsTheFuture() {
        runBlocking {
            val promise = Promise<Void>()
            promise.setOnCancel { promise.setCancelled()  }
            var timedout = false
            val job = launch {
                try {
                    withTimeout(100) {
                        promise.future.await()
                    }
                } catch (e: TimeoutCancellationException) {
                    timedout = true
                }
            }
            delay(100)
            job.join()
            Assert.assertTrue(timedout)
            Assert.assertTrue(promise.future.isCancelled)
        }
    }

}