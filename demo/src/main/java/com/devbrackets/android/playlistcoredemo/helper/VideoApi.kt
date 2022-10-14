package com.devbrackets.android.playlistcoredemo.helper

import android.net.Uri
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.devbrackets.android.exomedia.ui.widget.VideoView
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.listener.PlaylistListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcoredemo.data.MediaItem

class VideoApi(var videoView: VideoView?) : BaseMediaApi(), PlaylistListener<MediaItem> {
  init {
    videoView!!.setOnErrorListener(this)
    videoView!!.setOnPreparedListener(this)
    videoView!!.setOnCompletionListener(this)
    videoView!!.setOnSeekCompletionListener(this)
    videoView!!.setOnBufferUpdateListener(this)
  }

  override val isPlaying: Boolean
    get() = videoView!!.isPlaying

  override fun play() {
    videoView!!.start()
  }

  override fun pause() {
    videoView!!.pause()
  }

  override fun stop() {
    videoView!!.stopPlayback()
  }

  override fun reset() {
    // Purposefully left blank
  }

  override fun release() {
    videoView!!.suspend()
  }

  override fun setVolume(@FloatRange(from = 0.0, to = 1.0) left: Float, @FloatRange(from = 0.0, to = 1.0) right: Float) {
    videoView!!.volume = (left + right) / 2
  }

  override fun seekTo(@IntRange(from = 0L) milliseconds: Long) {
    videoView!!.seekTo(milliseconds.toInt().toLong())
  }

  override val handlesOwnAudioFocus: Boolean
    get() = false

  override fun handlesItem(item: MediaItem): Boolean {
    return item.mediaType == BasePlaylistManager.VIDEO
  }

  override fun playItem(item: MediaItem) {
    prepared = false
    bufferPercent = 0
    videoView!!.setVideoURI(Uri.parse(if (item.downloaded) item.downloadedMediaUri else item.mediaUrl))
  }

  override val currentPosition: Long
    get() = if (prepared) videoView!!.currentPosition else 0
  override val duration: Long
    get() = if (prepared) videoView!!.duration else 0
  override val bufferedPercent: Int
    get() = bufferPercent

  /*
   * PlaylistListener methods used for keeping the VideoControls provided
   * by the ExoMedia VideoView up-to-date with the current playback state
   */
  override fun onPlaylistItemChanged(currentItem: MediaItem?, hasNext: Boolean, hasPrevious: Boolean): Boolean {
    val videoControls = videoView!!.videoControls
    if (videoControls != null && currentItem != null) {
      // Updates the VideoControls display text
      videoControls.setTitle(currentItem.title)
      videoControls.setSubTitle(currentItem.album)
      videoControls.setDescription(currentItem.artist)

      // Updates the VideoControls button visibilities
      videoControls.setPreviousButtonEnabled(hasPrevious)
      videoControls.setNextButtonEnabled(hasNext)
    }
    return false
  }

  override fun onPlaybackStateChanged(playbackState: PlaybackState): Boolean {
    return false
  }
}