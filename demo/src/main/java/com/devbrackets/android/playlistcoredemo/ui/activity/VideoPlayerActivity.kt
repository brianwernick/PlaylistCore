package com.devbrackets.android.playlistcoredemo.ui.activity

import android.app.Activity
import android.os.Bundle
import com.devbrackets.android.exomedia.listener.VideoControlsSeekListener
import com.devbrackets.android.exomedia.ui.widget.VideoView
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.listener.PlaylistListener
import com.devbrackets.android.playlistcoredemo.App
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.devbrackets.android.playlistcoredemo.data.Samples
import com.devbrackets.android.playlistcoredemo.helper.VideoApi
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager
import java.util.*

class VideoPlayerActivity : Activity(), VideoControlsSeekListener, PlaylistListener<MediaItem> {
  protected var selectedIndex = 0
  protected var videoView: VideoView? = null
  protected var videoApi: VideoApi? = null
  protected val playlistManager: PlaylistManager by lazy {
    (applicationContext as App).playlistManager
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.video_player_activity)
    retrieveExtras()
    init()
  }

  override fun onStart() {
    super.onStart()
    playlistManager.registerPlaylistListener(this)
  }

  override fun onStop() {
    super.onStop()
    playlistManager.unRegisterPlaylistListener(this)
    playlistManager.removeVideoApi(videoApi!!)
    playlistManager.invokeStop()
  }

  override fun onSeekStarted(): Boolean {
    playlistManager.invokeSeekStarted()
    return true
  }

  override fun onSeekEnded(seekTime: Long): Boolean {
    playlistManager.invokeSeekEnded(seekTime)
    return true
  }

  override fun onPlaylistItemChanged(currentItem: MediaItem?, hasNext: Boolean, hasPrevious: Boolean): Boolean {
    // Purposefully left blank
    return false
  }

  override fun onPlaybackStateChanged(playbackState: PlaybackState): Boolean {
    if (playbackState === PlaybackState.STOPPED) {
      finish()
      return true
    }
    return false
  }

  /**
   * Retrieves the extra associated with the selected playlist index
   * so that we can start playing the correct item.
   */
  protected fun retrieveExtras() {
    val extras = intent.extras
    selectedIndex = extras?.getInt(EXTRA_INDEX, 0) ?: 0
  }

  protected fun init() {
    setupPlaylistManager()
    videoView = findViewById(R.id.video_play_activity_video_view)
    videoView?.setHandleAudioFocus(false)
    videoView?.videoControls?.setSeekListener(this)
    videoApi = VideoApi(videoView)

    playlistManager.addVideoApi(videoApi!!)
    playlistManager.play(0, false)
  }

  /**
   * Retrieves the playlist instance and performs any generation
   * of content if it hasn't already been performed.
   */
  private fun setupPlaylistManager() {
    val mediaItems: MutableList<MediaItem> = LinkedList()
    for (sample in Samples.video) {
      val mediaItem = MediaItem(sample, false)
      mediaItems.add(mediaItem)
    }
    playlistManager.setParameters(mediaItems, selectedIndex)
    playlistManager.id = PLAYLIST_ID.toLong()
  }

  companion object {
    const val EXTRA_INDEX = "EXTRA_INDEX"
    const val PLAYLIST_ID = 6 //Arbitrary, for the example (different from audio)
  }
}