package com.devbrackets.android.playlistcore.components.mediacontrols

import android.support.v4.media.session.MediaSessionCompat
import com.devbrackets.android.playlistcore.data.MediaInfo

interface MediaControlsProvider {
    fun update(mediaInfo: MediaInfo, mediaSession: MediaSessionCompat)
}