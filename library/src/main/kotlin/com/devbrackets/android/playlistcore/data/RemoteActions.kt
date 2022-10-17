package com.devbrackets.android.playlistcore.data

/**
 * A simple container for the remote actions used by the
 * [com.devbrackets.android.playlistcore.manager.BasePlaylistManager]
 * to inform the [BasePlaylistService] of processes to handle.
 */
object RemoteActions {
  private val PREFIX = "com.devbrackets.android.playlistcore."

  val ACTION_START_SERVICE = PREFIX + "start_service"

  val ACTION_PLAY_PAUSE = PREFIX + "play_pause"
  val ACTION_PREVIOUS = PREFIX + "previous"
  val ACTION_NEXT = PREFIX + "next"
  val ACTION_STOP = PREFIX + "stop"

  val ACTION_SEEK_STARTED = PREFIX + "seek_started"
  val ACTION_SEEK_ENDED = PREFIX + "seek_ended"

  //Extras
  val ACTION_EXTRA_SEEK_POSITION = PREFIX + "seek_position"
  val ACTION_EXTRA_START_PAUSED = PREFIX + "start_paused"
}
