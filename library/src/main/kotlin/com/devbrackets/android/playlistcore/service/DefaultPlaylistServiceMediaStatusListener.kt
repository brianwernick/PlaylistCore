package com.devbrackets.android.playlistcore.service

import android.support.annotation.IntRange
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.listener.MediaStatusListener

/**
 * A class to listen to the [MediaPlayerApi] events to control the
 * [PlaylistServiceCore]
 */
open class DefaultPlaylistServiceMediaStatusListener(protected val service: PlaylistServiceCore<*, *>) : MediaStatusListener {
    override fun onPrepared(mediaPlayer: MediaPlayerApi) {
        service.startMediaPlayer()
    }

    override fun onSeekComplete(mediaPlayer: MediaPlayerApi) {
        service.performOnSeekComplete()
    }

    override fun onCompletion(mediaPlayer: MediaPlayerApi) {
        service.performOnMediaCompletion()
    }

    override fun onError(mediaPlayer: MediaPlayerApi): Boolean {
        service.performOnMediaError()
        return false
    }

    override fun onBufferingUpdate(mediaPlayer: MediaPlayerApi, @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong()) percent: Int) {
        //Makes sure to update listeners of buffer updates even when playback is paused
        if (!mediaPlayer.isPlaying && service.currentMediaProgress.bufferPercent != percent) {
            service.currentMediaProgress.update(mediaPlayer.currentPosition, percent, mediaPlayer.duration)
            service.onProgressUpdated(service.currentMediaProgress)
        }
    }
}