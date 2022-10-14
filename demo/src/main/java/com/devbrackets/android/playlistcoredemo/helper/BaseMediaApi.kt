package com.devbrackets.android.playlistcoredemo.helper

import com.devbrackets.android.exomedia.listener.*
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcoredemo.data.MediaItem

abstract class BaseMediaApi :
  MediaPlayerApi<MediaItem>,
  OnPreparedListener,
  OnCompletionListener,
  OnErrorListener,
  OnSeekCompletionListener,
  OnBufferUpdateListener
{
  protected var prepared = false
  protected var bufferPercent = 0
  protected var statusListener: MediaStatusListener<MediaItem>? = null

  override fun setMediaStatusListener(listener: MediaStatusListener<MediaItem>) {
    statusListener = listener
  }

  override fun onCompletion() {
    statusListener?.onCompletion(this)
  }

  override fun onError(e: Exception): Boolean {
    return statusListener?.onError(this) == true
  }

  override fun onPrepared() {
    prepared = true
    statusListener?.onPrepared(this)
  }

  override fun onSeekComplete() {
    statusListener?.onSeekComplete(this)
  }

  override fun onBufferingUpdate(percent: Int) {
    bufferPercent = percent
    statusListener?.onBufferingUpdate(this, percent)
  }
}