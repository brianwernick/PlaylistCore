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

package com.devbrackets.android.playlistcore.helper

import android.content.Context
import android.media.AudioManager

/**
 * A helper to simplify audio focus procedures in to simple callbacks and/or
 * EventBus events.
 */
class AudioFocusHelper
/**
 * Creates and sets up the basic information for the AudioFocusHelper.  In order to
 * be of any use you must call [.setAudioFocusCallback]
 *
 * @param context The context for the AudioFocus (Generally Application)
 */
(context: Context) {
    /**
     * Basic AudioFocus callbacks.  These can also be accessed through
     * their corresponding EventBus events.
     */
    interface AudioFocusCallback {
        /**
         * Occurs when the application gains audio focus
         *
         * @return True if the event has been handled
         */
        fun onAudioFocusGained(): Boolean

        /**
         * Occurs when the application looses audio focus
         *
         * @return True if the event has been handled
         */
        fun onAudioFocusLost(canDuckAudio: Boolean): Boolean
    }

    enum class Focus {
        NONE, // We haven't tried to obtain focus
        NO_FOCUS_NO_DUCK, // Don't have focus, and can't duck
        NO_FOCUS_CAN_DUCK, // don't have focus but can play at low volume ("Ducking")
        FOCUSED             // have full audio focus
    }

    /**
     * Retrieves the current audio focus
     *
     * @return The current Focus value currently held
     */
    var currentAudioFocus = Focus.NONE
        protected set
    protected var audioManager: AudioManager
    protected var callbacks: AudioFocusCallback? = null
    protected var audioFocusListener = AudioFocusListener()

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * Sets the AudioFocusCallback to inform of focus changes.
     *
     * @param callback The Callback to inform
     */
    fun setAudioFocusCallback(callback: AudioFocusCallback?) {
        this.callbacks = callback
    }

    /**
     * Requests to obtain the audio focus
     *
     * @return True if the focus was granted
     */
    fun requestFocus(): Boolean {
        if (currentAudioFocus == Focus.FOCUSED) {
            return true
        }

        val status = audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status
    }

    /**
     * Requests the system to drop the audio focus
     *
     * @return True if the focus was lost
     */
    fun abandonFocus(): Boolean {
        if (currentAudioFocus == Focus.NONE) {
            return true
        }

        val status = audioManager.abandonAudioFocus(audioFocusListener)
        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status) {
            currentAudioFocus = Focus.NONE
        }

        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status
    }

    /**
     * An internal listener that pays attention to the events from the
     * Android system, converting the values to [Focus] for easier
     * digestion.
     */
    protected inner class AudioFocusListener : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    currentAudioFocus = Focus.FOCUSED
                    postAudioFocusGained()
                }

                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    currentAudioFocus = Focus.NO_FOCUS_NO_DUCK
                    postAudioFocusLost(false)
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    currentAudioFocus = Focus.NO_FOCUS_CAN_DUCK
                    postAudioFocusLost(true)
                }
            }
        }

        fun postAudioFocusGained() {
            callbacks?.onAudioFocusGained()
        }

        fun postAudioFocusLost(canDuck: Boolean) {
            callbacks?.onAudioFocusLost(canDuck)
        }
    }
}
