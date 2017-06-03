/*
 * Copyright (C) 2016 - 2017 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.util

import android.os.Handler
import android.os.HandlerThread

/**
 * A simple stopwatch to keep a correct and updated record of the running duration
 * of processes.
 */
class StopWatch {
    companion object {
        private val HANDLER_THREAD_NAME = "StopWatch_HandlerThread"
        private val DEFAULT_TICK_DELAY = 33 // ~30 fps
    }

    /**
     * Determines if the stopwatch is currently running
     *
     * @return True if the stopwatch is currently running
     */
    @Volatile
    var isRunning = false
        private set

    /**
     * The approximate duration between time updates
     */
    var tickDelay = DEFAULT_TICK_DELAY
    private var delayedHandler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var listener: TickListener? = null
    private val tickRunnable = TickRunnable()

    private var startTime: Long = 0
    private var currentTime: Long = 0
    private var storedTime: Long = 0

    /**
     * @param processOnStartingThread True if the repeating process should be handled on the same thread that created the Repeater
     */
    @JvmOverloads
    constructor(processOnStartingThread: Boolean = true) {
        if (processOnStartingThread) {
            delayedHandler = Handler()
        }
    }

    /**
     * @param handler The Handler to use for the repeating process
     */
    constructor(handler: Handler) {
        delayedHandler = handler
    }

    /**
     * Starts the stopwatch.  This will continue from where we last left off,
     * if you need to start from 0 call [.reset] first.
     */
    fun start() {
        if (isRunning) {
            return
        }

        isRunning = true
        startTime = System.currentTimeMillis()

        if (handlerThread == null) {
            handlerThread = HandlerThread(HANDLER_THREAD_NAME)
            handlerThread?.start()
            delayedHandler = Handler(handlerThread!!.looper)
        }

        tickRunnable.performTick()
    }

    /**
     * Stops the stopwatch, capturing the ending time
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        delayedHandler?.removeCallbacksAndMessages(null)
        handlerThread?.quit()

        isRunning = false
        currentTime = 0
        storedTime += System.currentTimeMillis() - startTime
    }

    /**
     * Resets the current time for the stopWatch
     */
    fun reset() {
        currentTime = 0
        storedTime = 0
        startTime = System.currentTimeMillis()
    }

    /**
     * Forcefully sets the current time for the stopwatch.
     *
     * @param time The new stopwatch time in milliseconds
     */
    fun overrideCurrentTime(time: Long) {
        startTime = System.currentTimeMillis()
        currentTime = 0
        storedTime = time
    }

    /**
     * Retrieves the current time for the stopwatch.  If the stopwatch is stopped then the
     * ending time will be returned.
     *
     * @return The time in milliseconds
     */
    val time: Long
        get() = currentTime + storedTime

    /**
     * Sets the listener to be notified for each time update (tick)
     *
     * @param listener The listener or null
     */
    fun setTickListener(listener: TickListener?) {
        this.listener = listener
    }

    interface TickListener {
        fun onStopWatchTick(currentTime: Long)
    }

    private inner class TickRunnable : Runnable {
        override fun run() {
            currentTime = System.currentTimeMillis() - startTime

            if (isRunning) {
                performTick()
            }

            listener?.onStopWatchTick(currentTime + storedTime)
        }

        fun performTick() {
            delayedHandler?.postDelayed(tickRunnable, tickDelay.toLong())
        }
    }
}
