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

package com.devbrackets.android.playlistcore.data

/**
 * An event to be used to inform listeners of media (e.g. audio, video) progress
 * changes.  This event will be re-used internally to avoid over-creating objects,
 * if you need to store the current values use [.obtain] to
 * create a duplicate of the current progress
 */
class MediaProgress(position: Long, bufferPercent: Int, duration: Long) {
    companion object {
        const val MAX_BUFFER_PERCENT = 100

        /**
         * Obtains a copy of the passed MediaProgress
         *
         * @param event The MediaProgress to copy
         * @return A copy of the event
         */
        fun obtain(event: MediaProgress): MediaProgress {
            return MediaProgress(event.position, event.bufferPercent, event.duration)
        }
    }

    var position: Long = 0
        get() = Math.max(0, field)
        set

    var duration: Long = 0
        set(duration) {
            field = Math.max(0, duration)
        }

    var bufferPercent: Int = 0
        set(bufferPercent) {
            field = Math.min(MAX_BUFFER_PERCENT, Math.max(0, bufferPercent))
            this.bufferPercentFloat = if (field == MAX_BUFFER_PERCENT) field.toFloat() else field.toFloat() / MAX_BUFFER_PERCENT.toFloat()
        }

    var bufferPercentFloat: Float = 0.toFloat()
        private set

    init {
        update(position, bufferPercent, duration)
    }

    fun update(position: Long, bufferPercent: Int, duration: Long) {
        this.position = position
        this.bufferPercent = bufferPercent
        this.duration = duration
    }
}
