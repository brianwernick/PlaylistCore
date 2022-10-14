package com.devbrackets.android.playlistcoredemo.service

import com.devbrackets.android.playlistcore.api.MediaPlayerApi.RemoteConnectionState
import com.devbrackets.android.playlistcore.components.playlisthandler.DefaultPlaylistHandler
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.service.BasePlaylistService
import com.devbrackets.android.playlistcoredemo.App
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.devbrackets.android.playlistcoredemo.helper.AudioApi
import com.devbrackets.android.playlistcoredemo.helper.cast.CastMediaPlayer
import com.devbrackets.android.playlistcoredemo.helper.cast.CastMediaPlayer.OnConnectionChangeListener
import com.devbrackets.android.playlistcoredemo.helper.cast.CastMediaPlayer.OnMediaInfoChangeListener
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager

/**
 * A simple service that extends [BasePlaylistService] in order to provide
 * the application specific information required.
 */
class MediaService : BasePlaylistService<MediaItem, PlaylistManager>(), OnConnectionChangeListener, OnMediaInfoChangeListener {

  override val playlistManager by lazy { (applicationContext as App).playlistManager }

  override fun onCreate() {
    super.onCreate()

    // Adds the audio player implementation, otherwise there's nothing to play media with
    playlistManager.mediaPlayers.add(CastMediaPlayer(applicationContext, this, this))
    playlistManager.mediaPlayers.add(AudioApi(applicationContext))
  }

  override fun onDestroy() {
    super.onDestroy()

    // Releases and clears all the MediaPlayersMediaImageProvider
    playlistManager.mediaPlayers.forEach {
      it.release()
    }

    playlistManager.mediaPlayers.clear()
  }

  override fun newPlaylistHandler(): PlaylistHandler<MediaItem> {
    val imageProvider = MediaImageProvider(applicationContext) {
      playlistHandler.updateMediaControls()
    }

    return DefaultPlaylistHandler.Builder(
      applicationContext,
      javaClass,
      playlistManager,
      imageProvider
    ).build()
  }

  /**
   * An implementation for the chromecast MediaPlayer [CastMediaPlayer] that allows us to inform the
   * [PlaylistHandler] that the state changed (which will handle swapping between local and remote playback).
   */
  override fun onCastMediaPlayerConnectionChange(player: CastMediaPlayer, state: RemoteConnectionState) {
    playlistHandler.onRemoteMediaPlayerConnectionChange(player, state)
  }

  /**
   * An implementation for the chromecast MediaPlayer [CastMediaPlayer] that allow us to inform the
   * [PlaylistHandler] that the information for the current media item has changed. This will normally
   * be called for state synchronization when we are informed that the media item has actually started, paused,
   * etc.
   */
  override fun onMediaInfoChange(player: CastMediaPlayer) {
    playlistHandler.updateMediaControls()
  }
}