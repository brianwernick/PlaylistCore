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
 * A method repeater to easily perform update functions on a timed basis.
 * **NOTE:** the duration between repeats may not be exact.  If you require an exact
 * amount of elapsed time use the [StopWatch] instead.
 */
class Repeater {
    companion object {
        private val HANDLER_THREAD_NAME = "Repeater_HandlerThread"
        private val DEFAULT_REPEAT_DELAY = 33 // ~30 fps
    }

    /**
     * Determines if the Repeater is currently running
     *
     * @return True if the repeater is currently running
     */
    @Volatile
    var isRunning = false
        private set

    var repeaterDelay = DEFAULT_REPEAT_DELAY
        /**
         * Retrieves the amount of time between method invocation.
         *
         * @return The millisecond time between method calls
         */
        get
        /**
         * Sets the amount of time between method invocation.
         *
         * @param milliSeconds The time between method calls [default: {@value #DEFAULT_REPEAT_DELAY}]
         */
        set

    private var delayedHandler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var listener: RepeatListener? = null
    private val pollRunnable = PollRunnable()

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
     * Starts the repeater
     */
    fun start() {
        if (!isRunning) {
            isRunning = true

            if (delayedHandler == null) {
                handlerThread = HandlerThread(HANDLER_THREAD_NAME)
                handlerThread?.start()
                delayedHandler = Handler(handlerThread!!.looper)
            }

            pollRunnable.performPoll()
        }
    }

    /**
     * Stops the repeater
     */
    fun stop() {
        handlerThread?.quit()
        isRunning = false
    }

    /**
     * Sets the listener to be notified for each repeat

     * @param listener The listener or null
     */
    fun setRepeatListener(listener: RepeatListener?) {
        this.listener = listener
    }

    interface RepeatListener {
        fun onRepeat()
    }

    private inner class PollRunnable : Runnable {
        override fun run() {
            listener?.onRepeat()

            if (isRunning) {
                performPoll()
            }
        }

        fun performPoll() {
            delayedHandler?.postDelayed(pollRunnable, repeaterDelay.toLong())
        }
    }
}
