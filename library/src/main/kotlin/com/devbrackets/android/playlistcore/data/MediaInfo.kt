package com.devbrackets.android.playlistcore.data

import android.graphics.Bitmap
import com.devbrackets.android.playlistcore.api.PlaylistItem

/**
 * An object to hold the information necessary to populate the
 * [PlaylistNotificationProvider] and [MediaSessionProvider]
 * with the information associated with the current playlist
 * item
 */
open class MediaInfo {
  var playlistItem: PlaylistItem? = null
  var largeNotificationIcon: Bitmap? = null
  var artwork: Bitmap? = null

  var appIcon: Int = 0
  var notificationId: Int = 0

  var mediaState: MediaState = MediaState()

  val title: String get() = playlistItem?.title.orEmpty()
  val album: String get() = playlistItem?.album.orEmpty()
  val artist: String get() = playlistItem?.artist.orEmpty()

  fun clear() {
    appIcon = 0
    notificationId = 0
    playlistItem = null

    largeNotificationIcon = null
    artwork = null
  }

  open class MediaState {
    var isPlaying: Boolean = false
    var isLoading: Boolean = false
    var isPreviousEnabled: Boolean = false
    var isNextEnabled: Boolean = false

    open fun reset() {
      isPlaying = false
      isLoading = false
      isPreviousEnabled = false
      isNextEnabled = false
    }
  }
}
