package com.devbrackets.android.playlistcore.listener

import android.support.annotation.IntRange
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.api.PlaylistItem

/**
 * Interface definition of a callback to be invoked indicating
 * the status changes for [MediaPlayerApi] implementations
 */
interface MediaStatusListener<I : PlaylistItem> {

    /**
     * Called when the media file is ready for playback.
     *
     * @param mediaPlayer the MediaPlayerApi that is ready for playback
     */
    fun onPrepared(mediaPlayer: MediaPlayerApi<I>)

    /**
     * Called to update status in buffering a media stream.
     * The received buffering percentage
     * indicates how much of the content has been buffered or played.
     * For example a buffering update of 80 percent when half the content
     * has already been played indicates that the next 30 percent of the
     * content to play has been buffered.
     *
     * @param mediaPlayer The MediaPlayerApi the update pertains to
     * @param percent The integer percent that is buffered [0, {@value MediaProgress#MAX_BUFFER_PERCENT}] inclusive
     */
    fun onBufferingUpdate(mediaPlayer: MediaPlayerApi<I>, @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong()) percent: Int)

    /**
     * Called to indicate the completion of a seek operation.
     *
     * @param mediaPlayer The MediaPlayerApi that issued the seek operation
     */
    fun onSeekComplete(mediaPlayer: MediaPlayerApi<I>)

    /**
     * Called when the end of a media source is reached during playback.
     *
     * @param mediaPlayer The MediaPlayerApi that reached the end of the file
     */
    fun onCompletion(mediaPlayer: MediaPlayerApi<I>)

    /**
     * Called to indicate an error.
     *
     * @param mediaPlayer The MediaPlayerApi the error pertains to
     * @return True if the method handled the error, false if it didn't.
     */
    fun onError(mediaPlayer: MediaPlayerApi<I>): Boolean
}