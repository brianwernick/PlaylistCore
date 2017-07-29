package com.devbrackets.android.playlistcore.api

import android.support.annotation.FloatRange
import android.support.annotation.IntRange
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.listener.MediaStatusListener

//TODO: maybe just call this a MediaPlayer and ignore the overlap with the Android mediaPlayer class?
interface MediaPlayerApi<I : PlaylistItem> {
    /**
     * Determines if media is currently playing on the
     * implementing object
     */
    val isPlaying: Boolean

    val handlesOwnAudioFocus: Boolean

    @get:IntRange(from = 0)
    val currentPosition: Long

    @get:IntRange(from = 0)
    val duration: Long

    /**
     * Retrieves the current buffer percent of the audio item.  If an audio item is not currently
     * prepared or buffering the value will be 0.  This should only be called after the audio item is
     * prepared (see [.setOnMediaPreparedListener])
     *
     * @return The integer percent that is buffered [0, {@value MediaProgress#MAX_BUFFER_PERCENT}] inclusive
     */
    @get:IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong())
    val bufferedPercent: Int

    fun play()

    fun pause()

    fun stop()

    fun reset()

    fun release()

    fun setVolume(@FloatRange(from = 0.0, to = 1.0) left: Float, @FloatRange(from = 0.0, to = 1.0) right: Float)

    fun seekTo(@IntRange(from = 0) milliseconds: Long)

    fun setMediaStatusListener(listener: MediaStatusListener<I>)

    fun handlesItem(item: I) : Boolean

    fun playItem(item: I)
}
