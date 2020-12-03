package com.softbankrobotics.dx.pepperextras

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aldebaran.qi.Promise
import com.softbankrobotics.dx.pepperextras.util.*
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class CoroutineScopeExtTest {

    @Test
    fun testCancellingAsyncFutureFutureCancelsTheCoroutine() {
        runBlocking {
            var job: Job? = null
            val future = asyncFuture {
                job = launch {
                    while (true) {
                        delay(500)
                    }
                }

            }
            delay(150)
            future.requestCancellation()
            runBlocking { future.awaitOrNull() }
            Assert.assertTrue(job!!.isCompleted)
            Assert.assertTrue(job!!.isCancelled)
        }
    }

    // Very useful feature: you can put a big try catch in your asyncFuture calls. If the
    // asyncFuture get cancelled, do any required cleaning into the catch.
    @Test
    fun testCancellingAsyncFutureCanBeCaught() {
        var exceptionCaught = false
        var before = false
        var after = false
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                before = true
                delay(5000)
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        future.cancel(true)
        runBlocking { future.awaitOrNull() }
        Assert.assertTrue(before)
        Assert.assertFalse(after)
        Assert.assertTrue(exceptionCaught)
    }

    // OK
    @Test
    fun testCancellingAsyncFutureCanBeCaughtWhenUsingAwait() {
        var exceptionCaught = false
        var before = false
        var after = false
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                before = true
                val p = Promise<Void>()
                p.future.await()
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        future.requestCancellation()
        runBlocking { future.awaitOrNull() }
        Assert.assertTrue(before)
        Assert.assertFalse(after)
        Assert.assertTrue(exceptionCaught)
    }

    @Test
    fun testAwaitOrNullThrowsIfCoroutineScopeCancelled() {
        var exceptionCaught = false
        var before = false
        var after = false
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                before = true
                val p = Promise<Void>()
                p.setOnCancel { p.setCancelled() }
                p.future.awaitOrNull()
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        future.requestCancellation()
        runBlocking { future.awaitOrNull() }
        Assert.assertTrue(before)
        Assert.assertFalse(after)
        Assert.assertTrue(exceptionCaught)
    }

    @Test
    fun testAwaitOrNullDoesNotThrowIfFutureCancelled() {
        var exceptionCaught = false
        var before = false
        var after = false
        val p = Promise<Void>()
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                var i = 0
                before = true
                p.setOnCancel { p.setCancelled() }
                p.future.awaitOrNull()
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        p.future.requestCancellation()
        runBlocking { future.awaitOrNull() }
        Assert.assertTrue(before)
        Assert.assertTrue(after)
        Assert.assertFalse(exceptionCaught)
    }

    @Test
    fun testIsActive() {
        var exceptionCaught = false
        var before = false
        var after = false
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                before = true
                while (isActive) { }
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        //future.cancel(true)
        future.requestCancellation()
        runBlocking { future.awaitOrNull() }
        Assert.assertTrue(before)
        Assert.assertTrue(after)
        Assert.assertFalse(exceptionCaught)
    }

    @Test
    fun makeSureAwaitStopExecutionIfCoroutineScopeWasCancelled() {
        var exceptionCaught = false
        var before = false
        var after = false
        var futureCancelledBefore = 0
        val p = Promise<Void>()
        val future = SingleThread.GlobalScope.asyncFuture {
            try {
                var i = 0
                while(i < 99999999) i+= 1
                if (futureCancelledBefore == 1)
                    futureCancelledBefore = 2
                before = true
                p.future.await()
                after = true
            } catch (e: CancellationException) {
                exceptionCaught = true
            }
        }
        runBlocking { delay(100) }
        future.requestCancellation()
        futureCancelledBefore = 1
        runBlocking { future.awaitOrNull() }
        Assert.assertEquals(2, futureCancelledBefore)
        Assert.assertTrue(before)
        Assert.assertFalse(after)
        Assert.assertTrue(exceptionCaught)
    }

    @Test
    fun testExecutionInSingleThreadedGlobalScopeExecuteInOneThread() {
        var refThread = ""
        var future = SingleThread.GlobalScope.asyncFuture {
            refThread = Thread.currentThread().name
        }
        runBlocking { future.awaitOrNull() }
        for (j in 1..10) {
            future = SingleThread.GlobalScope.asyncFuture {
                for (i in 1..10) {
                    delay(20)
                    Assert.assertEquals(refThread, Thread.currentThread().name)
                }
            }
            runBlocking { future.awaitOrNull() }
        }
    }

    @Test
    fun testCancellationDoesNotPropagateToChildrenOfGlobalScope() {
        var notCancelled = false
        val firstChild = SingleThread.GlobalScope.asyncFuture {
            // This first coroutine is not cancelled
            try {
                delay(2000)
                notCancelled = true
            } catch (e: CancellationException) {
                Log.i(TAG, "Child is cancelled !!")
            }
        }
        val secondChild = SingleThread.GlobalScope.asyncFuture {
            // Even if this one is cancelled
            delay(100)
            throw AssertionError("Boom")
        }
        runBlocking {
            secondChild.awaitOrNull()
            firstChild.awaitOrNull()
            Assert.assertTrue(notCancelled)
        }
    }

    @Test
    fun testCancellationPropagatesToChildrenOfNewCoroutineScope() {
        var notCancelled = false
        val scope = SingleThread.newCoroutineScope()
        val firstChild = scope.asyncFuture {
            // This first coroutine is cancelled
            try {
                delay(2000)
                notCancelled = true
            } catch (e: CancellationException) {
                Log.i(TAG, "Child is cancelled !!")
            }
        }
        val secondChild = scope.asyncFuture {
            // when this one is cancelled
            delay(100)
            throw AssertionError("Boom")
        }
        runBlocking {
            secondChild.awaitOrNull()
            firstChild.awaitOrNull()
            Assert.assertFalse(notCancelled)
        }
    }

    @Test
    fun testAsyncFutureLAZYStart() {
        var toggle = false
        val f = SingleThread.GlobalScope.asyncFuture(start = CoroutineStart.LAZY) {
            toggle = true
        }
        runBlocking {
            f.awaitOrNull()
            Assert.assertTrue(toggle)
        }
    }
}