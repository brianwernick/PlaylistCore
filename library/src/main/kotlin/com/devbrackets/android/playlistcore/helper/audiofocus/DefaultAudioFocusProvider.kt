package com.devbrackets.android.playlistcore.helper.audiofocus

import android.content.Context
import android.media.AudioManager

class DefaultAudioFocusProvider(context: Context) : AudioFocusProvider, AudioManager.OnAudioFocusChangeListener {
    companion object {
        const val AUDIOFOCUS_NONE = 0
    }

    private var currentAudioFocus = AUDIOFOCUS_NONE
    protected var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun requestFocus(): Boolean {
        if (currentAudioFocus == AudioManager.AUDIOFOCUS_GAIN) {
            return true
        }

        val status = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == status
    }

    override fun abandonFocus(): Boolean {
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
        currentAudioFocus = focusChange

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
//                postAudioFocusGained()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                //todo
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
//                postAudioFocusLost(false)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
//                postAudioFocusLost(true)
            }
        }
    }
}