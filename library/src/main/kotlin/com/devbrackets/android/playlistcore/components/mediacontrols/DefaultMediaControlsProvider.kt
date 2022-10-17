package com.devbrackets.android.playlistcore.components.mediacontrols

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.devbrackets.android.playlistcore.data.MediaInfo

open class DefaultMediaControlsProvider(protected val context: Context) : MediaControlsProvider {
  protected var enabled = true

  /**
   * Sets the volatile information for the remote views and controls.  This information is expected to
   * change frequently.
   *
   * @param mediaInfo The information representing the current media item
   * @param mediaSession The [MediaSessionCompat] to update with the information from [mediaInfo]
   */
  override fun update(mediaInfo: MediaInfo, mediaSession: MediaSessionCompat) {
    // Updates the available playback controls
    val playbackStateBuilder = PlaybackStateCompat.Builder()
    playbackStateBuilder.setActions(getPlaybackOptions(mediaInfo.mediaState))
    playbackStateBuilder.setState(getPlaybackState(mediaInfo.mediaState), mediaInfo.playbackPositionMs, 1.0f)

    mediaSession.setPlaybackState(playbackStateBuilder.build())

    if (enabled && !mediaSession.isActive) {
      mediaSession.isActive = true
    }
  }

  @PlaybackStateCompat.State
  protected open fun getPlaybackState(mediaState: MediaInfo.MediaState): Int {
    // NOTE: We should update this to properly include all of the information around playback to
    // set the stopped, error, etc. states as well
    return when {
      mediaState.isLoading -> PlaybackStateCompat.STATE_BUFFERING
      mediaState.isPlaying -> PlaybackStateCompat.STATE_PLAYING
      else -> PlaybackStateCompat.STATE_PAUSED
    }
  }

  @Deprecated(
    "This function is no longer used since it doesn't include the necessary information to correctly determine the state",
    replaceWith = ReplaceWith("getPlaybackState(mediaState)")
  )
  @PlaybackStateCompat.State
  protected open fun getPlaybackState(isPlaying: Boolean): Int {
    return PlaybackStateCompat.STATE_NONE
  }

  /**
   * Determines the available playback commands supported for the current media state
   *
   * @param mediaState The current media playback state
   * @return The available playback options
   */
  @PlaybackStateCompat.Actions
  protected open fun getPlaybackOptions(mediaState: MediaInfo.MediaState): Long {
    var availableActions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE

    if (mediaState.isNextEnabled) {
      availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
    }

    if (mediaState.isPreviousEnabled) {
      availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    }

    availableActions = availableActions or PlaybackStateCompat.ACTION_SEEK_TO

    return availableActions
  }
}