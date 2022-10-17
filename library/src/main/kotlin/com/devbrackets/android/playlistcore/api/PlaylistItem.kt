package com.devbrackets.android.playlistcore.api

import com.devbrackets.android.playlistcore.annotation.SupportedMediaType

interface PlaylistItem {
  val id: Long

  val downloaded: Boolean

  @SupportedMediaType
  val mediaType: Int

  val mediaUrl: String?

  val downloadedMediaUri: String?

  val thumbnailUrl: String?

  val artworkUrl: String?

  val title: String?

  val album: String?

  val artist: String?
}
