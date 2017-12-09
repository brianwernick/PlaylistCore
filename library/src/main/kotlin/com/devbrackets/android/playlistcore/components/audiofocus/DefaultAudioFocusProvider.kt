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

package com.devbrackets.android.playlistcore.components.audiofocus

import android.content.Context
import android.media.AudioManager
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.util.SimplifiedAudioManager

open class DefaultAudioFocusProvider<I : PlaylistItem>(context: Context) : AudioFocusProvider<I>, AudioManager.OnAudioFocusChangeListener {
    companion object {
        const val AUDIOFOCUS_NONE = 0
    }

    protected var pausedForFocusLoss = false
    protected var currentAudioFocus = AUDIOFOCUS_NONE
    protected var handler: PlaylistHandler<I>? = null

    protected var audioManager = SimplifiedAudioManager(context)

    override fun setPlaylistHandler(playlistHandler: PlaylistHandler<I>) {
        handler = playlistHandler
    }

    override fun refreshFocus() {
        if (handler?.currentMediaPlayer?.handlesOwnAudioFocus != false) {
            return
        }

        handleFocusChange(currentAudioFocus)
    }

    override fun requestFocus(): Boolean {
        if (handler?.currentMediaPlayer?.handlesOwnAudioFocus != false) {
            return false
        }

        if (currentAudioFocus == AudioManager.AUDIOFOCUS_GAIN) {
            return true
        }

        val status = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status
    }

    override fun abandonFocus(): Boolean {
        if (handler?.currentMediaPlayer?.handlesOwnAudioFocus != false) {
            return false
        }

        if (currentAudioFocus == AUDIOFOCUS_NONE) {
            return true
        }

        val status = audioManager.abandonAudioFocus(this)
        if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status) {
            currentAudioFocus = AUDIOFOCUS_NONE
        }

        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (currentAudioFocus == focusChange) {
            return
        }

        handleFocusChange(focusChange)
    }

    open fun handleFocusChange(newFocus: Int) {
        currentAudioFocus = newFocus
        if (handler?.currentMediaPlayer?.handlesOwnAudioFocus != false) {
            return
        }

        when (newFocus) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusGained()
            AudioManager.AUDIOFOCUS_LOSS -> onFocusLoss()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLossTransient()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusLossTransientCanDuck()
        }
    }

    open fun onFocusGained() {
        handler?.currentMediaPlayer?.let {
            if (pausedForFocusLoss && !it.isPlaying) {
                pausedForFocusLoss = false
                handler?.play()
            } else {
                it.setVolume(1.0f, 1.0f)
            }
        }
    }

    open fun onFocusLoss() {
        handler?.currentMediaPlayer?.let {
            if (it.isPlaying) {
                pausedForFocusLoss = true
                handler?.pause(false)
            }
        }
    }

    open fun onFocusLossTransient() {
        handler?.currentMediaPlayer?.let {
            if (it.isPlaying) {
                pausedForFocusLoss = true
                handler?.pause(true)
            }
        }
    }

    open fun onFocusLossTransientCanDuck() {
        handler?.currentMediaPlayer?.let {
            if (it.isPlaying) {
                it.setVolume(0.1f, 0.1f)
            }
        }
    }
}