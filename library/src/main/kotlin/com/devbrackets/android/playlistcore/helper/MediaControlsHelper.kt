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

package com.devbrackets.android.playlistcore.helper

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.annotation.DrawableRes
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.devbrackets.android.playlistcore.receiver.MediaControlsReceiver
import com.devbrackets.android.playlistcore.service.RemoteActions

/**
 * A class to help simplify playback control and artwork display for
 * remote views such as Android Wear, Bluetooth devices, Lock Screens, etc.
 * similar to how the [NotificationHelper] simplifies notifications
 */
class MediaControlsHelper
/**
 * Creates a new MediaControlsHelper object
 *
 * @param context The context to use for holding a MediaSession and sending action intents
 * @param mediaServiceClass The class for the service that owns the backing MediaService and to notify of playback actions
 */
(protected var context: Context, mediaServiceClass: Class<out Service>) {
    companion object {
        private val TAG = "MediaControlsHelper"
        val SESSION_TAG = "MediaControlsHelper.Session"
        val RECEIVER_EXTRA_CLASS = "com.devbrackets.android.playlistcore.RECEIVER_EXTRA_CLASS"
    }

    protected var appIconBitmap: Bitmap? = null
    protected var mediaSession: MediaSessionCompat

    protected var enabled = true

    init {
        val componentName = ComponentName(context, MediaControlsReceiver::class.java.name)

        mediaSession = MediaSessionCompat(context, SESSION_TAG, componentName, getMediaButtonReceiverPendingIntent(componentName, mediaServiceClass))
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(SessionCallback(mediaServiceClass))
    }

    fun release() {
        mediaSession.release()
        appIconBitmap = null
    }

    /**
     * Sets weather the RemoteViews and controls are shown when media is playing or
     * ready for playback (e.g. paused).  The information
     * will need to be updated by calling [.setBaseInformation]
     * and [.update]
     *
     * @param enabled True if the RemoteViews and controls should be shown
     */
    fun setMediaControlsEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }

        this.enabled = enabled

        //Remove the remote views and controls when disabling
        if (!enabled) {
            mediaSession.isActive = false
        }
    }

    /**
     * Sets the basic information for the remote views and controls that don't need to be updated.  Additionally, when
     * the mediaServiceClass is set the big notification will send intents to that service to notify of
     * button clicks.  These intents will have an action from
     *
     *  * [RemoteActions.ACTION_PLAY_PAUSE]
     *  * [RemoteActions.ACTION_PREVIOUS]
     *  * [RemoteActions.ACTION_NEXT]
     *
     * @param appIcon The applications icon resource
     */
    fun setBaseInformation(@DrawableRes appIcon: Int) {
        appIconBitmap = BitmapFactory.decodeResource(context.resources, appIcon)
    }

    /**
     * Sets the volatile information for the remote views and controls.  This information is expected to
     * change frequently.
     *
     * @param title The title to display for the notification (e.g. A song name)
     * @param album The name of the album the media is found in
     * @param artist The name of the artist for the media item
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    //getPlaybackOptions() and getPlaybackState() return the correctly annotated items
    fun update(title: String?, album: String?, artist: String?, mediaArtwork: Bitmap?,
               notificationMediaState: NotificationHelper.NotificationMediaState) {
        //Updates the current media MetaData
        val metaDataBuilder = MediaMetadataCompat.Builder()
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)

        if (appIconBitmap != null) {
            metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, appIconBitmap)
        }

        if (mediaArtwork != null) {
            metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaArtwork)
        }

        mediaSession?.setMetadata(metaDataBuilder.build())


        //Updates the available playback controls
        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setActions(getPlaybackOptions(notificationMediaState))
        playbackStateBuilder.setState(getPlaybackState(notificationMediaState.isPlaying), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession!!.setPlaybackState(playbackStateBuilder.build())
        Log.d(TAG, "update, controller is null ? " + if (mediaSession!!.controller == null) "true" else "false")

        if (enabled && !mediaSession!!.isActive) {
            mediaSession!!.isActive = true
        }
    }

    protected fun getMediaButtonReceiverPendingIntent(componentName: ComponentName, serviceClass: Class<out Service>): PendingIntent {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = componentName

        mediaButtonIntent.putExtra(RECEIVER_EXTRA_CLASS, serviceClass.name)
        return PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    @PlaybackStateCompat.State
    protected fun getPlaybackState(isPlaying: Boolean): Int {
        return if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
    }

    /**
     * Determines the available playback commands supported for the current media state
     *
     * @param mediaState The current media playback state
     * @return The available playback options
     */
    @PlaybackStateCompat.Actions
    protected fun getPlaybackOptions(mediaState: NotificationHelper.NotificationMediaState): Long {
        var availableActions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE

        if (mediaState.isNextEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (mediaState.isPreviousEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        return availableActions
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    protected fun createPendingIntent(action: String, serviceClass: Class<out Service>): PendingIntent {
        val intent = Intent(context, serviceClass)
        intent.action = action

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * A simple callback class to listen to the notifications received from the remote view
     * and forward them to the specified Class
     */
    protected inner class SessionCallback(serviceClass: Class<out Service>) : MediaSessionCompat.Callback() {
        protected var playPausePendingIntent: PendingIntent
        protected var nextPendingIntent: PendingIntent
        protected var previousPendingIntent: PendingIntent

        init {
            playPausePendingIntent = createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass)
            nextPendingIntent = createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass)
            previousPendingIntent = createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass)
        }

        override fun onPlay() {
            sendPendingIntent(playPausePendingIntent)
        }

        override fun onPause() {
            sendPendingIntent(playPausePendingIntent)
        }

        override fun onSkipToNext() {
            sendPendingIntent(nextPendingIntent)
        }

        override fun onSkipToPrevious() {
            sendPendingIntent(previousPendingIntent)
        }

        fun sendPendingIntent(pi: PendingIntent) {
            try {
                pi.send()
            } catch (e: Exception) {
                Log.d(TAG, "Error sending media controls pending intent", e)
            }

        }
    }
}
