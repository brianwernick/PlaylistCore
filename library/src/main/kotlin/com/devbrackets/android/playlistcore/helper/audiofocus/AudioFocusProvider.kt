package com.devbrackets.android.playlistcore.helper.audiofocus

interface AudioFocusProvider {
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