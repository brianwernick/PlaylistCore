package com.devbrackets.android.playlistcoredemo.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import com.devbrackets.android.exomedia.ui.listener.VideoControlsSeekListener
import com.devbrackets.android.exomedia.ui.widget.controls.DefaultVideoControls
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.listener.PlaylistListener
import com.devbrackets.android.playlistcoredemo.App
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.devbrackets.android.playlistcoredemo.data.Samples
import com.devbrackets.android.playlistcoredemo.databinding.VideoPlayerActivityBinding
import com.devbrackets.android.playlistcoredemo.helper.VideoApi
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager
import com.devbrackets.android.playlistcoredemo.ui.support.BindingActivity

class VideoPlayerActivity : BindingActivity<VideoPlayerActivityBinding>(), VideoControlsSeekListener, PlaylistListener<MediaItem> {
  companion object {
    const val EXTRA_INDEX = "EXTRA_INDEX"
    const val PLAYLIST_ID = 6 // Arbitrary for the example (different from audio)

    fun intent(context: Context, sampleIndex: Int): Intent {
      return Intent(context, VideoPlayerActivity::class.java).apply {
        putExtra(EXTRA_INDEX, sampleIndex)
      }
    }
  }

  private var videoApi: VideoApi? = null
  private val selectedIndex by lazy {
    intent.extras?.getInt(EXTRA_INDEX, 0) ?: 0
  }

  private val playlistManager: PlaylistManager by lazy {
    (applicationContext as App).playlistManager
  }

  override fun inflateBinding(layoutInflater: LayoutInflater): VideoPlayerActivityBinding {
    return VideoPlayerActivityBinding.inflate(layoutInflater)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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
    if (playbackState == PlaybackState.STOPPED) {
      finish()
      return true
    }

    return false
  }

  private fun init() {
    setupVideoView()
    setupPlaylistManager()

    playlistManager.play(0, false)
  }

  private fun setupVideoView() {
    binding.videoView.handleAudioFocus = false
    (binding.videoView.videoControls as? DefaultVideoControls)?.let {
      it.seekListener = this
    }

    videoApi = VideoApi(binding.videoView)
  }

  /**
   * Retrieves the playlist instance and performs any generation
   * of content if it hasn't already been performed.
   */
  private fun setupPlaylistManager() {
    val mediaItems = Samples.video.map { sample ->
      MediaItem(sample, false)
    }

    playlistManager.addVideoApi(videoApi!!)
    playlistManager.setParameters(mediaItems, selectedIndex)
    playlistManager.id = PLAYLIST_ID.toLong()
  }
}