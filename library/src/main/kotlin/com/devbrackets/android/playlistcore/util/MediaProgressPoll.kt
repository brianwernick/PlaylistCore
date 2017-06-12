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

import android.support.annotation.IntRange
import android.util.Log

import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcore.api.PlaylistItem

/**
 * A utility used to poll the progress of the currently playing media.
 * This will allows listeners to be informed of progress updates for display
 * or storage.
 */
class MediaProgressPoll<I : PlaylistItem> {
    companion object {
        private val TAG = "MediaProgressPoll"
    }

    protected var pollRepeater = Repeater()
    protected var overriddenPositionStopWatch = StopWatch()
    protected val currentMediaProgress = MediaProgress(0, 0, 0)

    /**
     * Specifies the listener to be informed of progress updates
     * at periodic intervals specified with [.setProgressPollDelay]
     */
    var progressListener: ProgressListener? = null

    protected var mediaPlayerApi: MediaPlayerApi<I>? = null

    protected var overridePosition = false
        /**
         * Sets if the audio position should be overridden, allowing the time to be restarted at will.  This
         * is useful for streaming audio where the audio doesn't have breaks between songs.
         *
         * @param value True if the position should be overridden
         */
        set(value) {
            if (value) {
                overriddenPositionStopWatch.start()
            } else {
                overriddenPositionStopWatch.stop()
            }

            field = value
        }

    /**
     * Specifies if the duration for the media currently being polled
     * should be overridden.  This should be called in conjunction with
     * [.setOverriddenDuration]
     */
    protected var overrideDuration = false
        set

    /**
     * Sets the amount of time to change the return value from [.getCurrentPosition].
     * This value will be reset when a new audio item is selected.
     */
    protected var positionOffset: Long = 0
        set

    /**
     * Specifies the value to use for returning the duration of the
     * media currently being polled.  This should only be used if the
     * media doesn't contain the duration, or the provided value is
     * incorrect.
     *
     * An example of this would be when streaming a live broadcast or
     * radio station.
     *
     * This should be called in conjunction with [.setOverrideDuration]
     */
    protected var overriddenDuration: Long = 0
        set

    /**
     * Retrieves the current position of the media item.  If no media item is currently
     * prepared or in playback then the value will be 0.

     * @return The millisecond value for the current position
     */
    protected val currentPosition: Long
        @IntRange(from = 0)
        get() {
            if (overridePosition) {
                return positionOffset + overriddenPositionStopWatch.time
            }

            return mediaPlayerApi?.currentPosition ?: 0
        }

    /**
     * Retrieves the duration of the current media item. If the value has
     * been overridden with [.setOverriddenDuration] then that value
     * will be returned instead.  If no media item is currently
     * prepared or in playback then the value will be 0.

     * @return The millisecond value for the duration
     */
    protected val duration: Long
        @IntRange(from = 0)
        get() {
            if (overrideDuration) {
                return overriddenDuration
            }

            return mediaPlayerApi?.duration ?: 0
        }

    /**
     * Retrieves the current buffer percent of the media item.  If no media item is currently
     * prepared or in playback then the value will be 0.
     */
    val bufferPercentage: Int
        @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong())
        get() = if (mediaPlayerApi != null) mediaPlayerApi!!.bufferedPercent else 0

    init {
        pollRepeater.setRepeatListener(OnRepeat())
    }

    /**
     * Starts polling for media progress.  If a progress listener has
     * not been specified with [.setProgressListener] then
     * the poll won't start.
     */
    fun start() {
        if (progressListener == null) {
            Log.w(TAG, "Not started due to no progress listener specified")
            return
        }

        pollRepeater.start()

        if (overridePosition) {
            overriddenPositionStopWatch.start()
        }
    }

    /**
     * Stops polling for progress
     */
    fun stop() {
        pollRepeater.stop()
        overriddenPositionStopWatch.stop()
    }

    /**
     * Stops the current progress poll and clears out the
     * previously set values with [.setOverridePosition],
     * [.setOverrideDuration], and [.setOverriddenDuration]
     */
    fun reset() {
        stop()
        overriddenPositionStopWatch.reset()

        positionOffset = 0
        overriddenDuration = 0
    }

    /**
     * Releases all resources associated with the poll including
     * the media player specified with [.update] and
     * the progress listener specified with [.setProgressListener].
     *
     *
     * NOTE: this calls [.reset]
     */
    fun release() {
        reset()
        mediaPlayerApi = null
        progressListener = null
    }

    /**
     * Updates the media player that should be continuously
     * polled.  This will reset any previously specified values
     * with [.reset]

     * @param mediaPlayerApi The new [MediaPlayerApi] that should be polled or null
     */
    fun update(mediaPlayerApi: MediaPlayerApi<I>?) {
        this.mediaPlayerApi = mediaPlayerApi
        reset()
    }

    /**
     * Sets the delay to use when notifying of progress.  The
     * default is 33 milliseconds, or 30 frames-per-second

     * @param milliSeconds The millisecond delay to use
     */
    fun setProgressPollDelay(milliSeconds: Int) {
        pollRepeater.repeaterDelay = milliSeconds
    }

    /**
     * Restarts the audio position to the start if the position is being overridden (see [.setOverridePosition]).
     * This will be the value specified with [.setPositionOffset] or 0 if it hasn't been set.
     */
    fun restartOverridePosition() {
        overriddenPositionStopWatch.reset()
    }

    /**
     * Performs the actual periodic polling of the progress and informing the
     * listener.  If the listener has not been specified, or was set to null, then
     * the polling will be stopped.
     */
    protected inner class OnRepeat : Repeater.RepeatListener {
        override fun onRepeat() {
            currentMediaProgress.update(currentPosition, bufferPercentage, duration)
            if (progressListener != null) {
                progressListener?.onProgressUpdated(currentMediaProgress)
            } else {
                pollRepeater.stop()
                Log.w(TAG, "Stopping due to no listeners")
            }
        }
    }
}
