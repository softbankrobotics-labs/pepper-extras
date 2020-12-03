package com.softbankrobotics.dx.pepperextras.actuation.internal

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope


abstract class RunnableAction<T, ChildAsync: RunnableAction<T, ChildAsync>.Async> {

    protected val ACTION_NAME = javaClass.simpleName

    fun run(): T {
        return FutureUtils.get(async().run())
    }

    protected abstract val _asyncInstance: ChildAsync

    fun async(): ChildAsync {
        return _asyncInstance
    }

    abstract inner class Async protected constructor() {

        private var future: Future<T> = Future.cancelled()
        protected val TAG = ACTION_NAME

        fun run(scope: CoroutineScope = SingleThread.GlobalScope): Future<T>
                = scope.asyncFuture(CoroutineName(ACTION_NAME)) {
            try {
                Log.d(TAG, "Action starting")
                if (!future.isDone) {
                    throw RuntimeException("You are trying to start a $ACTION_NAME " +
                            "action that is already running. All the subsequent calls to run() of a" +
                            " running $ACTION_NAME action are ignored.")
                }
                future = _run(scope)
                future.await()
            } catch (e: Throwable) {
                Log.e(TAG, "Noticed exception: $e")
                throw e
            }
            finally {
                Log.d(TAG, "Action stopping")
            }
        }

        protected abstract fun _run(scope: CoroutineScope): Future<T>
    }
}
