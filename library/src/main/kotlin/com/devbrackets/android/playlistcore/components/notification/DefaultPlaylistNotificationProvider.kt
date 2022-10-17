package com.devbrackets.android.playlistcore.components.notification

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat.Action
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.data.MediaInfo
import com.devbrackets.android.playlistcore.data.RemoteActions

/**
 * A default implementation for the [PlaylistNotificationProvider] that will correctly
 * handle both normal and large notifications, remote action registration, categories,
 * channels, etc.
 *
 * *Note* The default channel this provides only supports an English name and description
 * so it is recommended that you extend this class and override [buildNotificationChannel]
 * to provide your own custom name and description.
 */
open class DefaultPlaylistNotificationProvider(protected val context: Context) : PlaylistNotificationProvider {
  companion object {
    const val CHANNEL_ID = "PlaylistCoreMediaNotificationChannel"
  }

  protected val notificationManager: NotificationManager by lazy {
    context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  }

  protected open val clickPendingIntent: PendingIntent?
    get() = null

  override fun buildNotification(info: MediaInfo, mediaSession: MediaSessionCompat, serviceClass: Class<out Service>): Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      buildNotificationChannel()
    }

    return NotificationCompat.Builder(context, CHANNEL_ID).apply {
      setSmallIcon(info.appIcon)
      setLargeIcon(info.largeNotificationIcon)

      var contentText = info.album
      if (info.artist.isNotBlank()) {
        contentText += if (contentText.isNotBlank()) " - " + info.artist else info.artist
      }

      setContentTitle(info.title)
      setContentText(contentText)

      setContentIntent(clickPendingIntent)
      setDeleteIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))

      val allowSwipe = !info.mediaState.isPlaying
      setAutoCancel(allowSwipe)
      setOngoing(!allowSwipe)

      setCategory(Notification.CATEGORY_TRANSPORT)
      setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

      setActions(this, info, serviceClass)
      setStyle(buildMediaStyle(mediaSession, serviceClass))
    }.build()
  }

  protected open fun setActions(builder: NotificationCompat.Builder, info: MediaInfo, serviceClass: Class<out Service>) {
    builder.addAction(previousAction(info.mediaState, serviceClass))
    builder.addAction(playPauseAction(info.mediaState, serviceClass))
    builder.addAction(nextAction(info.mediaState, serviceClass))
  }

  protected open fun playPauseAction(
    mediaState: MediaInfo.MediaState,
    serviceClass: Class<out Service>
  ): Action {
    val title = when(mediaState.isPlaying) {
      true -> context.resources.getString(R.string.playlistcore_default_notification_pause)
      false -> context.resources.getString(R.string.playlistcore_default_notification_play)
    }

    val icon = when {
      mediaState.isPlaying && mediaState.isLoading ->  R.drawable.playlistcore_notification_pause_disabled
      mediaState.isPlaying && !mediaState.isLoading -> R.drawable.playlistcore_notification_pause
      !mediaState.isPlaying && mediaState.isLoading -> R.drawable.playlistcore_notification_play_disabled
      else -> R.drawable.playlistcore_notification_play
    }

    val pendingIntent = createPendingIntent(serviceClass, RemoteActions.ACTION_PLAY_PAUSE)
    return Action(icon, title, pendingIntent)
  }

  protected open fun previousAction(
    mediaState: MediaInfo.MediaState,
    serviceClass: Class<out Service>
  ): Action {
    val title = context.resources.getString(R.string.playlistcore_default_notification_previous)
    val icon = when(mediaState.isPreviousEnabled) {
      true ->  R.drawable.playlistcore_notification_previous
      false -> R.drawable.playlistcore_notification_previous_disabled
    }

    val pendingIntent = createPendingIntent(serviceClass, RemoteActions.ACTION_PREVIOUS)
    return Action(icon, title, pendingIntent)
  }

  protected open fun nextAction(
    mediaState: MediaInfo.MediaState,
    serviceClass: Class<out Service>
  ): Action {
    val title = context.resources.getString(R.string.playlistcore_default_notification_next)
    val icon = when(mediaState.isNextEnabled) {
      true ->  R.drawable.playlistcore_notification_next
      false -> R.drawable.playlistcore_notification_next_disabled
    }

    val pendingIntent = createPendingIntent(serviceClass, RemoteActions.ACTION_NEXT)
    return Action(icon, title, pendingIntent)
  }

  protected open fun buildMediaStyle(mediaSession: MediaSessionCompat, serviceClass: Class<out Service>): MediaStyle {
    return with(MediaStyle()) {
      setMediaSession(mediaSession.sessionToken)
      setShowActionsInCompactView(0, 1, 2) // previous, play/pause, next
      setShowCancelButton(true)
      setCancelButtonIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))
    }
  }

  /**
   * Builds the notification channel using the default name and description (English Only)
   * if the channel hasn't already been created
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected open fun buildNotificationChannel() {
    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
      val name = context.resources.getString(R.string.playlistcore_default_notification_channel_name)
      val description = context.resources.getString(R.string.playlistcore_default_notification_channel_description)
      buildNotificationChannel(name, description)
    }
  }

  /**
   * Builds the notification channel using the specified name and description
   */
  @TargetApi(Build.VERSION_CODES.O)
  protected open fun buildNotificationChannel(name: CharSequence, description: String) {
    val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
    channel.description = description
    channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    notificationManager.createNotificationChannel(channel)
  }

  /**
   * Creates a PendingIntent for the given action to the specified service
   *
   * @param action The action to use
   * @param serviceClass The service class to notify of intents
   * @return The resulting PendingIntent
   */
  protected open fun createPendingIntent(serviceClass: Class<out Service>, action: String): PendingIntent {
    val intent = Intent(context, serviceClass)
    intent.action = action

    return PendingIntent.getService(context, 0, intent, getIntentFlags())
  }

  protected open fun getIntentFlags(): Int {
    return when {
      Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> PendingIntent.FLAG_UPDATE_CURRENT
      else -> PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
  }
}