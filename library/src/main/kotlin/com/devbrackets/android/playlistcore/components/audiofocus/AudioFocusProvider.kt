package com.devbrackets.android.playlistcore.components.audiofocus

import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler

interface AudioFocusProvider<I: PlaylistItem> {
    fun setPlaylistHandler(playlistHandler: PlaylistHandler<I>)

    /**
     * Requests to obtain the audio focus
     *
     * @return `true` if the focus was granted
     */
    fun requestFocus(): Boolean

    /**
     * Requests the system to drop the audio focus
     *
     * @return `true` if the focus was lost
     */
    fun abandonFocus(): Boolean
}