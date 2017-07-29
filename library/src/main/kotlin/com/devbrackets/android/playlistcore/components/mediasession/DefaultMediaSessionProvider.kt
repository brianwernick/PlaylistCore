package com.devbrackets.android.playlistcore.components.mediasession

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.devbrackets.android.playlistcore.data.MediaInfo
import com.devbrackets.android.playlistcore.receiver.MediaControlsReceiver
import com.devbrackets.android.playlistcore.service.RemoteActions

open class DefaultMediaSessionProvider(val context: Context, val serviceClass: Class<out Service>) : MediaSessionCompat.Callback(), MediaSessionProvider {
    companion object {
        val SESSION_TAG = "DefaultMediaSessionProvider.Session"
        val RECEIVER_EXTRA_CLASS = "com.devbrackets.android.playlistcore.RECEIVER_EXTRA_CLASS"
    }

    protected var playPausePendingIntent = createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass)
    protected var nextPendingIntent = createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass)
    protected var previousPendingIntent = createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass)

    protected val mediaSession: MediaSessionCompat by lazy {
        val componentName = ComponentName(context, MediaControlsReceiver::class.java.name)
        MediaSessionCompat(context, SESSION_TAG, componentName, getMediaButtonReceiverPendingIntent(componentName))
    }

    override fun get(): MediaSessionCompat {
        return mediaSession
    }

    override fun update(mediaInfo: MediaInfo) {
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(this)

        // Updates the current media MetaData
        val metaDataBuilder = MediaMetadataCompat.Builder()
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaInfo.title)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mediaInfo.album)
        metaDataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaInfo.artist)

        // Updates the icon
        BitmapFactory.decodeResource(context.resources, mediaInfo.appIcon)?.let {
            metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
        }

        // Updates the artwork
        if (mediaInfo.artwork != null) {
            metaDataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mediaInfo.artwork)
        }

        mediaSession.setMetadata(metaDataBuilder.build())
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

    protected open fun getMediaButtonReceiverPendingIntent(componentName: ComponentName): PendingIntent {
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = componentName

        mediaButtonIntent.putExtra(RECEIVER_EXTRA_CLASS, serviceClass.name)
        return PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT)
    }

    protected open fun sendPendingIntent(pi: PendingIntent) {
        try {
            pi.send()
        } catch (e: Exception) {
            Log.d("DefaultMediaSessionPro", "Error sending media controls pending intent", e)
        }
    }
}