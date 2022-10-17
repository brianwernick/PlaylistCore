package com.devbrackets.android.playlistcore.listener

import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.service.BasePlaylistService
import com.devbrackets.android.playlistcore.data.PlaybackState

/**
 * A simple callback interface for listening to [BasePlaylistService]
 * changes.
 */
interface PlaylistListener<in T : PlaylistItem> {

  /**
   * Occurs when the currently playing item has changed
   *
   * @return True if the event has been handled
   */
  fun onPlaylistItemChanged(currentItem: T?, hasNext: Boolean, hasPrevious: Boolean): Boolean

  /**
   * Occurs when the current media state changes
   *
   * @return True if the event has been handled
   */
  fun onPlaybackStateChanged(playbackState: PlaybackState): Boolean
}
