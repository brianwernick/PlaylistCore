package com.devbrackets.android.playlistcore.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.annotation.RequiresApi

open class AudioManagerCompat(context: Context) {
    protected val audioManager: AudioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    protected var currentAudioFocusRequest: Any? = null

    open fun requestAudioFocus(listener: AudioManager.OnAudioFocusChangeListener, streamType: Int, durationHint: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return audioManager.requestAudioFocus(listener, streamType, durationHint)
        }

        val audioAttributes = AudioAttributes.Builder()
                .setContentType(mapStreamTypeToContentType(streamType))
                .build()

        currentAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(listener)
                .setAudioAttributes(audioAttributes)
                .build()

        return audioManager.requestAudioFocus(currentAudioFocusRequest as AudioFocusRequest)
    }

    open fun abandonAudioFocus(listener: AudioManager.OnAudioFocusChangeListener): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return audioManager.abandonAudioFocus(listener)
        }

        return audioManager.abandonAudioFocusRequest(currentAudioFocusRequest as AudioFocusRequest)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected open fun mapStreamTypeToContentType(streamType: Int): Int {
        return when (streamType) {
            AudioManager.STREAM_MUSIC -> AudioAttributes.CONTENT_TYPE_MUSIC
            else -> AudioAttributes.CONTENT_TYPE_UNKNOWN
        }
    }
}