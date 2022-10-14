package com.devbrackets.android.playlistcoredemo.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.devbrackets.android.playlistcore.components.image.ImageProvider
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.data.MediaItem

class MediaImageProvider(context: Context, listener: OnImageUpdatedListener) : ImageProvider<MediaItem> {
  fun interface OnImageUpdatedListener {
    fun onImageUpdated()
  }

  private val glide: RequestManager
  private val listener: OnImageUpdatedListener
  private val notificationImageTarget = NotificationImageTarget()
  private val remoteViewImageTarget = RemoteViewImageTarget()
  private val defaultNotificationImage: Bitmap
  private var notificationImage: Bitmap? = null
  override var remoteViewArtwork: Bitmap? = null
    private set

  init {
    glide = Glide.with(context.applicationContext)
    this.listener = listener
    defaultNotificationImage = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
  }

  override val notificationIconRes: Int
    get() = R.mipmap.ic_launcher
  override val remoteViewIconRes: Int
    get() = R.mipmap.ic_launcher
  override val largeNotificationImage: Bitmap?
    get() = if (notificationImage != null) notificationImage else defaultNotificationImage

  override fun updateImages(playlistItem: MediaItem) {
    glide.asBitmap().load(playlistItem.thumbnailUrl).into<NotificationImageTarget>(notificationImageTarget)
    glide.asBitmap().load(playlistItem.artworkUrl).into<RemoteViewImageTarget>(remoteViewImageTarget)
  }

  /**
   * A class used to listen to the loading of the large notification images and perform
   * the correct functionality to update the notification once it is loaded.
   *
   * **NOTE:** This is a Glide Image loader class
   */
  private inner class NotificationImageTarget : SimpleTarget<Bitmap?>() {
    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
      notificationImage = resource
      listener.onImageUpdated()
    }
  }

  /**
   * A class used to listen to the loading of the large lock screen images and perform
   * the correct functionality to update the artwork once it is loaded.
   *
   * **NOTE:** This is a Glide Image loader class
   */
  private inner class RemoteViewImageTarget : SimpleTarget<Bitmap?>() {
    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
      remoteViewArtwork = resource
      listener.onImageUpdated()
    }
  }
}