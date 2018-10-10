/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.javafx

import javafx.animation.*
import javafx.application.*
import javafx.event.*
import javafx.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Dispatches execution onto JavaFx application thread and provides native [delay] support.
 */
@Suppress("unused")
public val Dispatchers.JavaFx: JavaFxDispatcher
    get() = kotlinx.coroutines.javafx.JavaFx

/**
 * Dispatcher for JavaFx application thread with support for [awaitPulse].
 *
 * This class provides type-safety and a point for future extensions.
 */
public sealed class JavaFxDispatcher : MainCoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) = Platform.runLater(block)

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val timeline = schedule(timeMillis, TimeUnit.MILLISECONDS, EventHandler {
            with(continuation) { resumeUndispatched(Unit) }
        })
        continuation.invokeOnCancellation { timeline.stop() }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        val timeline = schedule(timeMillis, TimeUnit.MILLISECONDS, EventHandler {
            block.run()
        })
        return object : DisposableHandle {
            override fun dispose() {
                timeline.stop()
            }
        }
    }

    private fun schedule(time: Long, unit: TimeUnit, handler: EventHandler<ActionEvent>): Timeline =
        Timeline(KeyFrame(Duration.millis(unit.toMillis(time).toDouble()), handler)).apply { play() }
}

internal class JavaFxDispatcherFactory : MainDispatcherFactory {
    override fun createDispatcher(): MainCoroutineDispatcher = JavaFx

    override val loadPriority: Int
        get() = 1 // Swing has 0
}

private object ImmediateJavaFxDispatcher : JavaFxDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = this

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Platform.isFxApplicationThread()

    override fun toString() = "JavaFx [immediate]"
}

/**
 * Dispatches execution onto JavaFx application thread and provides native [delay] support.
 */
internal object JavaFx : JavaFxDispatcher() {
    init {
        // :kludge: to make sure Toolkit is initialized if we use JavaFx dispatcher outside of JavaFx app
        initPlatform()
    }

    override val immediate: MainCoroutineDispatcher
        get() = ImmediateJavaFxDispatcher

    override fun toString() = "JavaFx"
}

private val pulseTimer by lazy {
    PulseTimer().apply { start() }
}

/**
 * Suspends coroutine until next JavaFx pulse and returns time of the pulse on resumption.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException][kotlinx.coroutines.CancellationException].
 */
public suspend fun awaitPulse(): Long = suspendCancellableCoroutine { cont ->
    pulseTimer.onNext(cont)
}

private class PulseTimer : AnimationTimer() {
    val next = CopyOnWriteArrayList<CancellableContinuation<Long>>()

    override fun handle(now: Long) {
        val cur = next.toTypedArray()
        next.clear()
        for (cont in cur)
            with (cont) { JavaFx.resumeUndispatched(now) }
    }

    fun onNext(cont: CancellableContinuation<Long>) {
        next += cont
    }
}

internal fun initPlatform() {
    // Ad-hoc workaround for #443. Will be fixed with multi-release jar.
    // If this code throws an exception (Java 9 + prohibited reflective access), initialize JavaFX directly
    Class.forName("com.sun.javafx.application.PlatformImpl")
        .getMethod("startup", java.lang.Runnable::class.java)
        .invoke(null, java.lang.Runnable { })
}