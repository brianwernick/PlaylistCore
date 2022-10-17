package com.devbrackets.android.playlistcore.data

enum class PlaybackState {
  /**
   * Media is currently being retrieved to be prepared and played. This is the first state
   * entered for playback.
   */
  RETRIEVING,

  /**
   * Media is currently being prepared for playback. This typically means that the media is being
   * buffered, synced, etc. and occurs after [RETRIEVING]
   */
  PREPARING,

  /**
   * Media is currently being played.
   */
  PLAYING,

  /**
   * Media is currently prepared but has been paused. Playback can be started at any time
   */
  PAUSED,

  /**
   * Media playback is currently seeking to a requested timestamp. This can be started from either
   * [PLAYING] or [PAUSED]
   */
  SEEKING,

  /**
   * No media is currently being prepared to play, playing, paused, etc.
   */
  STOPPED,

  /**
   * An error occurred when playing back media. This is effectively the same as [STOPPED]
   * but only accessible through an error
   */
  ERROR
}