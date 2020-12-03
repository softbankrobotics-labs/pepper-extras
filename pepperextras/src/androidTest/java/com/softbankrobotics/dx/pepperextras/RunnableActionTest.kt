package com.softbankrobotics.dx.pepperextras

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aldebaran.qi.Future
import com.softbankrobotics.dx.pepperextras.util.*
import com.softbankrobotics.dx.pepperextras.actuation.internal.RunnableAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

class MyIntAction internal constructor(): RunnableAction<Int, MyIntAction.Async>() {

    override val _asyncInstance = Async()

    inner class Async internal constructor(): RunnableAction<Int, MyIntAction.Async>.Async() {

        override fun _run(scope: CoroutineScope): Future<Int> {
            return Future.of(42)
        }
    }
}

class MyLongAction internal constructor(): RunnableAction<Int, MyLongAction.Async>() {

    override val _asyncInstance = Async()

    inner class Async internal constructor(): RunnableAction<Int, MyLongAction.Async>.Async() {

        override fun _run(scope: CoroutineScope): Future<Int> = scope.asyncFuture {
            for (i in 0..50)
                delay(100)
            42
        }
    }
}


class MyMultiFunAction internal constructor(): RunnableAction<Unit, MyMultiFunAction.Async>() {

    override val _asyncInstance = Async()
    inner class Async internal constructor(): RunnableAction<Unit, MyMultiFunAction.Async>.Async() {

        override fun _run(scope: CoroutineScope): Future<Unit> { return Future.of(null) }

        fun bidulle(): Future<Int> {return Future.of(42) }
    }
}


@RunWith(AndroidJUnit4::class)
class RunnableActionTest {

    @Test
    fun testRunnableActionRun() {
        runBlocking {
            val test = MyIntAction()
            Assert.assertEquals(42, test.run())
            Assert.assertEquals(42, test.async().run().await())
        }
    }

    @Test
    fun testOnlyOneRunnableActionCanRun() {
        runBlocking {
            val test = MyLongAction()
            test.async().run()
            val f2 = test.async().run()
            Assert.assertEquals(true, f2.hasError())
            Assert.assertEquals(
                    "You are trying to start a MyLongAction action that is already running. " +
                            "All the subsequent calls to run() of a running MyLongAction action are " +
                            "ignored.", f2.errorMessage)
        }
    }

    @Test
    fun testMultiFunAction() {
        runBlocking {
            val test = MyMultiFunAction()
            Assert.assertEquals(42, test.async().bidulle().await())
        }
    }
}
