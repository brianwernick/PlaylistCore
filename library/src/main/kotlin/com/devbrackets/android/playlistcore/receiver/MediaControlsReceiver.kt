/*
 * Copyright (C) 2016 - 2017 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.receiver

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper
import com.devbrackets.android.playlistcore.service.RemoteActions

/**
 * A Receiver to handle remote controls from devices
 * such as Bluetooth and Android Wear
 */
class MediaControlsReceiver : BroadcastReceiver() {
    companion object {
        private val TAG = "MediaControlsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            handleMediaButtonIntent(context, intent)
        }
    }

    /**
     * Performs the functionality to handle the [Intent.ACTION_MEDIA_BUTTON] intent
     * action.  This will pass the appropriate value to the [com.devbrackets.android.playlistcore.service.PlaylistServiceCore]
     *
     * @param context The Context the intent was received with
     * @param intent The Intent that was received
     */
    private fun handleMediaButtonIntent(context: Context, intent: Intent) {
        val mediaServiceClass = getServiceClass(intent, MediaControlsHelper.RECEIVER_EXTRA_CLASS)
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
    private fun handleKeyEvent(context: Context, mediaServiceClass: Class<out Service>, keyEvent: KeyEvent) {
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
    private fun getServiceClass(intent: Intent, key: String): Class<out Service>? {
        var serviceClass: Class<out Service>? = null
        val className = intent.getStringExtra(key)
        if (className != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                serviceClass = Class.forName(className) as Class<out Service>
            } catch (e: Exception) {
                //todo log
                //Purposefully left blank
            }
        }

        return serviceClass
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    private fun createPendingIntent(context: Context, action: String, serviceClass: Class<out Service>): PendingIntent {
        val intent = Intent(context, serviceClass)
        intent.action = action

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Attempts to send the pending intent
     *
     * @param pi The pending intent to send
     */
    private fun sendPendingIntent(pi: PendingIntent) {
        try {
            pi.send()
        } catch (e: Exception) {
            Log.d(TAG, "Error sending media controls pending intent", e)
        }
    }
}
