package com.devbrackets.android.playlistcore.helper.mediacontrols

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo
import com.devbrackets.android.playlistcore.helper.notification.NotificationMediaState

open class DefaultMediaControlsProvider(protected val context: Context) : MediaControlsProvider {
    protected var enabled = true

    /**
     * Sets the volatile information for the remote views and controls.  This information is expected to
     * change frequently.
     *
     * @param title The title to display for the notification (e.g. A song name)
     * @param album The name of the album the media is found in
     * @param artist The name of the artist for the media item
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    override fun update(mediaInfo: MediaInfo, mediaSession: MediaSessionCompat) {
        //Updates the available playback controls
        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setActions(getPlaybackOptions(mediaInfo.mediaState))
        playbackStateBuilder.setState(getPlaybackState(mediaInfo.mediaState.isPlaying), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession.setPlaybackState(playbackStateBuilder.build())

        if (enabled && !mediaSession.isActive) {
            mediaSession.isActive = true
        }
    }

    @PlaybackStateCompat.State
    protected open fun getPlaybackState(isPlaying: Boolean): Int {
        //todo We should be handling all the appropriate states instead of just these 2 (i.e. the mediaInfo has a state instead of booleans)
        return if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_CONNECTING
    }

    /**
     * Determines the available playback commands supported for the current media state
     *
     * @param mediaState The current media playback state
     * @return The available playback options
     */
    @PlaybackStateCompat.Actions
    protected open fun getPlaybackOptions(mediaState: NotificationMediaState): Long {
        var availableActions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE

        if (mediaState.isNextEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (mediaState.isPreviousEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        return availableActions
    }
}