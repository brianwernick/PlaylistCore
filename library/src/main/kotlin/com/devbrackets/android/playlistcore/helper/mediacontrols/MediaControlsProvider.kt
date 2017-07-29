package com.devbrackets.android.playlistcore.helper.mediacontrols

import android.support.v4.media.session.MediaSessionCompat
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo

interface MediaControlsProvider {
    fun update(mediaInfo: MediaInfo, mediaSession: MediaSessionCompat)
}