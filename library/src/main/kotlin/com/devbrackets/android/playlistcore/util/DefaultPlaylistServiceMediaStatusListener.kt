package com.devbrackets.android.playlistcore.util

import android.support.annotation.IntRange
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.service.BasePlaylistService

/**
 * A class to listen to the [MediaPlayerApi] events to control the
 * [BasePlaylistService]
 */
open class DefaultPlaylistServiceMediaStatusListener<I : PlaylistItem>(protected val service: BasePlaylistService<*, *>) : MediaStatusListener<I> {
    override fun onPrepared(mediaPlayer: MediaPlayerApi<I>) {
        service.startMediaPlayer()
    }

    override fun onSeekComplete(mediaPlayer: MediaPlayerApi<I>) {
        service.performOnSeekComplete()
    }

    override fun onCompletion(mediaPlayer: MediaPlayerApi<I>) {
        service.performOnMediaCompletion()
    }

    override fun onError(mediaPlayer: MediaPlayerApi<I>): Boolean {
        service.performOnMediaError()
        return false
    }

    override fun onBufferingUpdate(mediaPlayer: MediaPlayerApi<I>, @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong()) percent: Int) {
        //Makes sure to update listeners of buffer updates even when playback is paused
        if (!mediaPlayer.isPlaying && service.currentMediaProgress.bufferPercent != percent) {
            service.currentMediaProgress.update(mediaPlayer.currentPosition, percent, mediaPlayer.duration)
            service.onProgressUpdated(service.currentMediaProgress)
        }
    }
}