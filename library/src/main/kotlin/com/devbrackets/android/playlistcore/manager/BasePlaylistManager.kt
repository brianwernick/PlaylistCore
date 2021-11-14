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

package com.devbrackets.android.playlistcore.manager

import android.app.Application
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import androidx.annotation.IntRange
import android.util.Log
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.data.PlaylistItemChange
import com.devbrackets.android.playlistcore.data.RemoteActions
import com.devbrackets.android.playlistcore.listener.PlaybackStatusListener
import com.devbrackets.android.playlistcore.listener.PlaylistListener
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcore.service.BasePlaylistService
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * A manager to keep track of a playlist of items that a service can use for playback.
 * Additionally, this manager provides methods for interacting with the specified service
 * to simplify and standardize implementations in the service itself.  This manager can be
 * used as standalone with a custom service, or in conjunction with
 * [BasePlaylistService]
 */
abstract class BasePlaylistManager<I : PlaylistItem>(protected val application: Application, protected val mediaServiceClass: Class<out Service>) :
        PlaylistListener<I>, ProgressListener {
    companion object {
        private val TAG = "PlaylistManager"

        const val INVALID_ID = -1L
        const val INVALID_POSITION = -1

        /**
         * A flag used to represent either an Audio item or
         * support for Audio items.  This is a flag that is
         * referenced by [.allowedTypeFlag] and
         * [PlaylistItem.mediaType]
         */
        const val AUDIO = 1

        /**
         * A flag used to represent either a Video item or
         * support for Video items.  This is a flag that is
         * referenced by [.allowedTypeFlag] and
         * [PlaylistItem.mediaType]
         */
        const val VIDEO = 1 shl 1
    }

    /**
     * Determines if there is another item in the play list after the current one.
     *
     * @return True if there is an item after the current one
     */
    open val isNextAvailable: Boolean
        get() = currentPosition != INVALID_POSITION && currentPosition + 1 < itemCount

    /**
     * Determines if there is an item in the play list before the current one.
     *
     * @return True if there is an item before the current one
     */
    open val isPreviousAvailable: Boolean
        get() = currentPosition > 0

    /**
     * Retrieves the Item representing the currently selected
     * item.  If there aren't any items in the play list then null will
     * be returned instead.
     *
     * @return The current Item or null
     */
    open val currentItem: I?
        get() {
            if (currentPosition != INVALID_POSITION && currentPosition < itemCount) {
                return getItem(currentPosition)
            }

            return null
        }

    /**
     * Returns the current size of the playlist.
     *
     * @return The size of the playlist
     */
    @get:IntRange(from = 0)
    abstract val itemCount: Int

    @IntRange(from = INVALID_POSITION.toLong())
    var currentPosition = INVALID_POSITION
        set(value) {
            field = if (value < 0 || value >= itemCount) {
                INVALID_POSITION
            } else {
                value
            }
        }

    @IntRange(from = INVALID_ID)
    var id = INVALID_ID

    var playlistHandler: PlaylistHandler<I>? = null
    var playbackStatusListener: PlaybackStatusListener<I>? = null
    val mediaPlayers = mutableListOf<MediaPlayerApi<I>>()

    protected var playlistListeners: MutableList<WeakReference<PlaylistListener<I>>> = LinkedList()
    protected var progressListeners: MutableList<WeakReference<ProgressListener>> = LinkedList()
    protected var playlistListenersLock = ReentrantLock(true)
    protected var progressListenersLock = ReentrantLock(true)

    protected var seekEndedIntent: Intent? = null
    protected var playPausePendingIntent: PendingIntent? = null
    protected var nextPendingIntent: PendingIntent? = null
    protected var previousPendingIntent: PendingIntent? = null
    protected var stopPendingIntent: PendingIntent? = null
    protected var seekStartedPendingIntent: PendingIntent? = null

    init {
        @Suppress("LeakingThis")
        constructControlIntents(mediaServiceClass, application)
    }

    /**
     * Resets the current positions and ids
     */
    open fun reset() {
        id = INVALID_ID
        currentPosition = INVALID_POSITION
    }

    /**
     * This is a pass through method that is called from the [BasePlaylistService] to inform
     * any listeners that are registered through [.registerPlaylistListener]
     *
     * @param currentItem The new playback item
     * @param hasNext True if there exists an item after the `currentItem` in the playlist
     * @param hasPrevious True if there exists an item before the `currentItem` in the playlist
     * @return `true` if the event should be consumed
     */
    override fun onPlaylistItemChanged(currentItem: I?, hasNext: Boolean, hasPrevious: Boolean): Boolean {
        return notifyListeners(playlistListenersLock, playlistListeners) {
            it.onPlaylistItemChanged(currentItem, hasNext, hasPrevious)
        }
    }

    /**
     * This is a pass through method that is called from the [BasePlaylistService] to inform
     * any listeners that are registered through [.registerPlaylistListener]
     *
     * @param playbackState The new media playback state
     * @return True if the event should be consumed
     */
    override fun onPlaybackStateChanged(playbackState: PlaybackState): Boolean {
        return notifyListeners(playlistListenersLock, playlistListeners) {
            it.onPlaybackStateChanged(playbackState)
        }
    }

    /**
     * This is a pass through method that is called from the [BasePlaylistService] to inform
     * any listeners that are registered through [.registerPlaylistListener]
     *
     * @param mediaProgress The current media progress
     * @return True if the mediaProgress should be consumed
     */
    override fun onProgressUpdated(mediaProgress: MediaProgress): Boolean {
        return notifyListeners(progressListenersLock, progressListeners) {
            it.onProgressUpdated(mediaProgress)
        }
    }

    /**
     * Retrieves the most recent media playback state.
     *
     * @return The most recent PlaybackState
     */
    open val currentPlaybackState: PlaybackState
        get() = playlistHandler?.currentPlaybackState ?: PlaybackState.STOPPED

    /**
     * Retrieves the current progress for the media playback
     *
     * @return The most recent progress event
     */
    open val currentProgress: MediaProgress?
        get() = playlistHandler?.currentMediaProgress

    /**
     * Retrieves the most recent [PlaylistItemChange]
     *
     * @return The most recent Item Changed information
     */
    open val currentItemChange: PlaylistItemChange<I>?
        get() = playlistHandler?.currentItemChange

    /**
     * Registers the listener to this service.  These callbacks will only be
     * called if [.registerService] has been called.

     * @param listener The listener to register
     */
    open fun registerPlaylistListener(listener: PlaylistListener<I>) {
        playlistListenersLock.lock()
        playlistListeners.add(WeakReference(listener))
        playlistListenersLock.unlock()
    }

    /**
     * UnRegisters the specified listener.  This should be called when the listener
     * class losses focus, or should be destroyed.

     * @param listener The listener to remove
     */
    open fun unRegisterPlaylistListener(listener: PlaylistListener<*>) {
        playlistListenersLock.lock()
        val iterator = playlistListeners.iterator()

        while (iterator.hasNext()) {
            val playlistListener = iterator.next().get()
            if (playlistListener == null || playlistListener == listener) {
                iterator.remove()
            }
        }

        playlistListenersLock.unlock()
    }

    /**
     * Registers the listener to be notified of progress updates.
     *
     * @param listener The listener to notify of progress updates
     */
    open fun registerProgressListener(listener: ProgressListener) {
        progressListenersLock.lock()
        progressListeners.add(WeakReference(listener))
        progressListenersLock.unlock()
    }

    /**
     * UnRegisters the specified listener.  This should only be called for listeners
     * that have been registered with [.registerProgressListener]
     *
     * @param listener The listener to unregister
     */
    open fun unRegisterProgressListener(listener: ProgressListener) {
        progressListenersLock.lock()
        val iterator = progressListeners.iterator()

        while (iterator.hasNext()) {
            val progressListener = iterator.next().get()
            if (progressListener == null || progressListener == listener) {
                iterator.remove()
            }
        }

        progressListenersLock.unlock()
    }

    /**
     * Performs the functionality to play the current item in the playlist.  This will
     * interact with the service specified with [.getMediaServiceClass].  If there
     * are no items in the current playlist then no action will be performed.
     *
     * @param seekPosition The position to start the current items playback at (milliseconds)
     * @param startPaused True if the media item should not start playing when it has been prepared
     */
    open fun play(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        currentItem ?: return

        //Starts the playlist service
        val intent = Intent(application, mediaServiceClass)
        intent.action = RemoteActions.ACTION_START_SERVICE
        intent.putExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, seekPosition)
        intent.putExtra(RemoteActions.ACTION_EXTRA_START_PAUSED, startPaused)
        application.startService(intent)
    }

    /**
     * Attempts to find the position for the item with the specified itemId.  If no
     * such item exists then the current position will NOT be modified.  However if the item
     * is found then that position will be used to update the current position.  You can also
     * manually set the current position with [.setCurrentPosition].
     *
     * @param itemId The items id to use for finding the new position
     */
    open fun setCurrentItem(@IntRange(from = 0) itemId: Long) {
        currentPosition = getPositionForItem(itemId)
    }

    /**
     * Determines the position for the item with the passed id.

     * @param itemId The items id to use for finding the position
     * *
     * @return The items position or [.INVALID_POSITION]
     */
    @IntRange(from = INVALID_POSITION.toLong())
    abstract fun getPositionForItem(@IntRange(from = 0) itemId: Long): Int

    /**
     * Determines if the given ItemQuery is the same as the current item

     * @param item The ItemQuery to compare to the current item
     * *
     * @return True if the current item matches the passed item
     */
    open fun isPlayingItem(item: I?): Boolean {
        val workingCurrentItem = currentItem

        return item != null && workingCurrentItem != null && item.id == workingCurrentItem.id
    }

    /**
     * Retrieves the item at the given position in the playlist.  If the playlist
     * is null or the position is out of bounds then null will be returned.
     *
     * @param position The position in the playlist to grab the item for
     * @return The retrieved item or null
     */
    abstract fun getItem(@IntRange(from = 0) position: Int): I?

    /**
     * Updates the currently selected item to the next one and retrieves the
     * Item representing that item.  If there aren't any items in the play
     * list or there isn't a next item then null will be returned.
     *
     * @return The next Item or null
     */
    open fun next(): I? {
        if (currentPosition != INVALID_POSITION) {
            currentPosition = Math.min(currentPosition + 1, itemCount)
        }

        return currentItem
    }

    /**
     * Updates the currently selected item to the previous one and retrieves the
     * Item representing that item.  If there aren't any items in the play
     * list or there isn't a previous item then null will be returned.
     *
     * @return The previous Item or null
     */
    open fun previous(): I? {
        if (currentPosition != INVALID_POSITION) {
            currentPosition = Math.max(0, currentPosition - 1)
        }

        return currentItem
    }

    /**
     * Informs the Media service that the current item
     * needs to be played/paused.  The service specified with
     * [.getMediaServiceClass]} will be informed using the action
     * [RemoteActions.ACTION_PLAY_PAUSE]
     */
    open fun invokePausePlay() {
        sendPendingIntent(playPausePendingIntent)
    }

    /**
     * Informs the Media service that we need to seek to
     * the next item. The service specified with
     * [.getMediaServiceClass] will be informed using the action
     * [RemoteActions.ACTION_NEXT]
     */
    open fun invokeNext() {
        sendPendingIntent(nextPendingIntent)
    }

    /**
     * Informs the Media service that we need to seek to
     * the previous item. The service specified with
     * [.getMediaServiceClass] will be informed using the action
     * [RemoteActions.ACTION_PREVIOUS]
     */
    open fun invokePrevious() {
        sendPendingIntent(previousPendingIntent)
    }

    /**
     * Informs the Media service that we need to stop
     * playback. The service specified with
     * [.getMediaServiceClass] will be informed using the action
     * [RemoteActions.ACTION_STOP]
     */
    open fun invokeStop() {
        sendPendingIntent(stopPendingIntent)
    }

    /**
     * Informs the Media service that we have started seeking
     * the playback.  The service specified with
     * [.getMediaServiceClass] will be informed using the action
     * [RemoteActions.ACTION_SEEK_STARTED]
     */
    open fun invokeSeekStarted() {
        sendPendingIntent(seekStartedPendingIntent)
    }

    /**
     * Informs the Media service that we need to seek
     * the current item. The service specified with
     * [.getMediaServiceClass] will be informed using the action
     * [RemoteActions.ACTION_SEEK_ENDED] and have an intent extra with the
     * key [RemoteActions.ACTION_EXTRA_SEEK_POSITION] (long)
     */
    open fun invokeSeekEnded(@IntRange(from = 0) seekPosition: Long) {
        //Tries to start the intent
        seekEndedIntent?.let {
            it.putExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, seekPosition)
            application.startService(it)
        }
    }

    protected inline fun <T> notifyListeners(lock: ReentrantLock, list: MutableList<WeakReference<T>>, handler: (T) -> Boolean) : Boolean {
        lock.lock()
        val iterator = list.iterator()

        while (iterator.hasNext()) {
            val listener = iterator.next().get()
            if (listener == null) {
                iterator.remove()
                continue
            }

            if (handler.invoke(listener)) {
                lock.unlock()
                return true
            }
        }

        lock.unlock()
        return false
    }

    /**
     * Creates the Intents that will be used to interact with the playlist service

     * @param mediaServiceClass The class to inform of any media playback controls
     * *
     * @param application The application to use when constructing the intents used to inform the playlist service of invocations
     */
    protected open fun constructControlIntents(mediaServiceClass: Class<out Service>, application: Application) {
        previousPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_PREVIOUS)
        nextPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_NEXT)
        playPausePendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_PLAY_PAUSE)

        stopPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_STOP)
        seekStartedPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_SEEK_STARTED)

        seekEndedIntent = Intent(application, mediaServiceClass).apply {
            action = RemoteActions.ACTION_SEEK_ENDED
        }
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param application The application to use when creating the  pending intent
     * @param serviceClass The service class to notify of intents
     * @param action The action to use
     * @return The resulting PendingIntent
     */
    protected open fun createPendingIntent(application: Application, serviceClass: Class<out Service>, action: String): PendingIntent {
        val intent = Intent(application, serviceClass)
        intent.action = action

        return PendingIntent.getService(application, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Attempts to send the specified PendingIntent.

     * @param pi The pending intent to send
     */
    protected open fun sendPendingIntent(pi: PendingIntent?) {
        try {
            pi?.send()
        } catch (e: Exception) {
            Log.d(TAG, "Error sending pending intent " + pi.toString(), e)
        }
    }
}
