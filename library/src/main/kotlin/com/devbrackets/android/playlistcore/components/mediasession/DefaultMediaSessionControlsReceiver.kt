package com.devbrackets.android.playlistcore.components.mediasession

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.devbrackets.android.playlistcore.data.RemoteActions

/**
 * A Receiver to handle remote controls from devices
 * such as Bluetooth and Android Wear
 */
open class DefaultMediaSessionControlsReceiver : BroadcastReceiver() {
  companion object {
    private val TAG = "DefaultControlsReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
      handleMediaButtonIntent(context, intent)
    }
  }

  /**
   * Performs the functionality to handle the [Intent.ACTION_MEDIA_BUTTON] intent
   * action.  This will pass the appropriate value to the [com.devbrackets.android.playlistcore.service.BasePlaylistService]
   *
   * @param context The Context the intent was received with
   * @param intent The Intent that was received
   */
  protected open fun handleMediaButtonIntent(context: Context, intent: Intent) {
    val mediaServiceClass = getServiceClass(intent, DefaultMediaSessionProvider.RECEIVER_EXTRA_CLASS)
    val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

    if (mediaServiceClass != null && event != null && event.action == KeyEvent.ACTION_UP) {
      handleKeyEvent(context, mediaServiceClass, event)
    }
  }

  /**
   * Handles the media button click events
   *
   * @param context The context to use when informing the media service of the event
   * @param mediaServiceClass The service class to inform of the event
   * @param keyEvent The KeyEvent associated with the button click
   */
  protected open fun handleKeyEvent(context: Context, mediaServiceClass: Class<out Service>, keyEvent: KeyEvent) {
    when (keyEvent.keyCode) {
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> sendPendingIntent(createPendingIntent(context, RemoteActions.ACTION_PLAY_PAUSE, mediaServiceClass))
      KeyEvent.KEYCODE_MEDIA_NEXT -> sendPendingIntent(createPendingIntent(context, RemoteActions.ACTION_NEXT, mediaServiceClass))
      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> sendPendingIntent(createPendingIntent(context, RemoteActions.ACTION_PREVIOUS, mediaServiceClass))
    }
  }

  /**
   * Retrieves the class from the intent with the specified key if it exists
   *
   * @param intent The Intent to retrieve the class associated with `key`
   * @param key The key to retrieve the class with
   * @return The stored class or null
   */
  @Suppress("UNCHECKED_CAST")
  protected open fun getServiceClass(intent: Intent, key: String): Class<out Service>? {
    return intent.getStringExtra(key)?.let {
      try {
        Class.forName(it) as Class<out Service>
      } catch (e: Exception) {
        Log.e(TAG, "Unable to determine service from extras", e)
        null
      }
    }
  }

  /**
   * Creates a PendingIntent for the given action to the specified service
   *
   * @param action The action to use
   * @param serviceClass The service class to notify of intents
   * @return The resulting PendingIntent
   */
  protected open fun createPendingIntent(context: Context, action: String, serviceClass: Class<out Service>): PendingIntent {
    val intent = Intent(context, serviceClass)
    intent.action = action

    return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /**
   * Attempts to send the pending intent
   *
   * @param pi The pending intent to send
   */
  protected open fun sendPendingIntent(pi: PendingIntent) {
    try {
      pi.send()
    } catch (e: Exception) {
      Log.d(TAG, "Error sending media controls pending intent", e)
    }
  }
}