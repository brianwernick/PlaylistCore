package com.devbrackets.android.playlistcore.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.devbrackets.android.playlistcore.annotation.ServiceContinuationMethod
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.data.RemoteActions
import com.devbrackets.android.playlistcore.listener.ServiceCallbacks
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager

/**
 * A base service for adding media playback support using the [BasePlaylistManager].
 *
 *
 * This service will request a wifi wakelock if the item being played isn't
 * downloaded (see [.isDownloaded]) and the manifest permission
 * &lt;uses-permission android:name="android.permission.WAKE_LOCK" /&gt;
 * has been specified.  This will allow the audio to be played without interruptions.
 *
 *
 * Due to not knowing the actual class for the playlist service, if you want to handle
 * audio becoming noisy (e.g. when a headphone cable is pulled out) then you will need
 * to create your own [android.content.BroadcastReceiver] as outlined at
 * [http://developer.android.com/guid/topics/media/mediaplayer.html#noisyintent](http://developer.android.com/guide/topics/media/mediaplayer.html#noisyintent)
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class BasePlaylistService<I : PlaylistItem, out M : BasePlaylistManager<I>> : Service(), ServiceCallbacks {
  companion object {
    private const val TAG = "BasePlaylistService"
  }

  protected var inForeground: Boolean = false

  /**
   * Links the [BasePlaylistManager] that contains the information for playback
   * to this service.
   *
   * NOTE: this is only used for retrieving information, it isn't used to register notifications
   * for playlist changes, however as long as the change isn't breaking (e.g. cleared playlist)
   * then nothing additional needs to be performed
   */
  protected abstract val playlistManager: M

  /**
   * Retrieves the continuity bits associated with the service.  These
   * are the bits returned by [onStartCommand]
   */
  @ServiceContinuationMethod
  protected open val serviceContinuationMethod: Int
    get() = Service.START_NOT_STICKY

  protected val playlistHandler: PlaylistHandler<I> by lazy {
    newPlaylistHandler()
  }

  abstract fun newPlaylistHandler(): PlaylistHandler<I>

  override fun onBind(intent: Intent): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()

    playlistHandler.setup(this)
  }

  override fun onTaskRemoved(rootIntent: Intent) {
    // Only destroy when paused/stopped, otherwise we want to keep the service active
    if (!inForeground) {
      onDestroy()
    }
  }

  /**
   * Stops the current media in playback and releases all
   * held resources.
   */
  override fun onDestroy() {
    playlistHandler.tearDown()
    super.onDestroy()
  }

  override fun stop() {
    stopSelf()
  }

  override fun runAsForeground(notificationId: Int, notification: Notification) {
    if (inForeground) {
      return
    }

    inForeground = true
    startForeground(notificationId, notification)
  }

  override fun endForeground(dismissNotification: Boolean) {
    if (!inForeground) {
      return
    }

    inForeground = false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val flag = when (dismissNotification) {
        true -> STOP_FOREGROUND_REMOVE
        else -> STOP_FOREGROUND_DETACH
      }
      stopForeground(flag)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(dismissNotification)
    }
  }

  /**
   * Handles the intents posted by the [BasePlaylistManager] through
   * the `invoke*` methods.
   */
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == null) {
      Log.d(TAG, "Ignoring empty playlist service intent action")
      return serviceContinuationMethod
    }

    handleRemoteAction(intent.action, intent.extras)
    return serviceContinuationMethod
  }

  /**
   * Handles the remote actions from the big notification and media controls
   * to control the media playback
   *
   * @param action The action from the intent to handle
   * @param extras The extras packaged with the intent associated with the action
   * @return True if the remote action was handled
   */
  protected open fun handleRemoteAction(action: String?, extras: Bundle?): Boolean {
    if (action == null || action.isEmpty()) {
      return false
    }

    when (action) {
      RemoteActions.ACTION_START_SERVICE -> {
        val seekToPosition = extras?.getLong(RemoteActions.ACTION_EXTRA_SEEK_POSITION, -1) ?: -1
        val startPaused = extras?.getBoolean(RemoteActions.ACTION_EXTRA_START_PAUSED, false) ?: false
        playlistHandler.startItemPlayback(seekToPosition, startPaused)
      }
      RemoteActions.ACTION_PLAY_PAUSE -> playlistHandler.togglePlayPause()
      RemoteActions.ACTION_NEXT -> playlistHandler.next()
      RemoteActions.ACTION_PREVIOUS -> playlistHandler.previous()
      RemoteActions.ACTION_STOP -> playlistHandler.stop()
      RemoteActions.ACTION_SEEK_STARTED -> playlistHandler.startSeek()
      RemoteActions.ACTION_SEEK_ENDED -> playlistHandler.seek(extras?.getLong(RemoteActions.ACTION_EXTRA_SEEK_POSITION, 0) ?: 0)

      else -> return false
    }

    return true
  }
}
