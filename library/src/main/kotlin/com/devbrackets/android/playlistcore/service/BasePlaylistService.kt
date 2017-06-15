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

package com.devbrackets.android.playlistcore.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.support.annotation.FloatRange
import android.util.Log
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.annotation.ServiceContinuationMethod
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.event.PlaylistItemChange
import com.devbrackets.android.playlistcore.helper.AudioFocusHelper
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper
import com.devbrackets.android.playlistcore.helper.image.ImageProvider
import com.devbrackets.android.playlistcore.helper.mediasession.DefaultMediaSessionProvider
import com.devbrackets.android.playlistcore.helper.mediasession.MediaSessionProvider
import com.devbrackets.android.playlistcore.helper.notification.DefaultPlaylistNotificationProvider
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo
import com.devbrackets.android.playlistcore.helper.notification.PlaylistNotificationProvider
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcore.listener.ServiceListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.util.DefaultPlaylistServiceMediaStatusListener
import com.devbrackets.android.playlistcore.util.MediaProgressPoll

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
 *
 * TODO: separate the actual functionality from the service
 */
abstract class BasePlaylistService<I : PlaylistItem, out M : BasePlaylistManager<I>> : Service(), AudioFocusHelper.AudioFocusCallback, ProgressListener {
    companion object {
        private val TAG = "BasePlaylistService"
    }

    enum class PlaybackState {
        RETRIEVING, // the MediaRetriever is retrieving music
        PREPARING, // Preparing / Buffering
        PLAYING, // Active but could be paused due to loss of audio focus Needed for returning after we regain focus
        PAUSED, // Paused but player ready
        SEEKING, // performSeek was called, awaiting seek completion callback
        STOPPED, // Stopped not preparing media
        ERROR          // An error occurred, we are stopped
    }

    protected var wifiLock: WifiManager.WifiLock? = null
    protected var audioFocusHelper: AudioFocusHelper? = null

    protected var mediaProgressPoll = MediaProgressPoll<I>()
    protected var mediaListener = DefaultPlaylistServiceMediaStatusListener<I>(this)

    protected var currentMediaPlayer: MediaPlayerApi<I>? = null
    protected val mediaPlayers = mutableListOf<MediaPlayerApi<I>>()

    /**
     * Retrieves the current playback progress
     */
    var currentMediaProgress = MediaProgress(0, 0, 0)
        protected set

    /**
     * Retrieves the current playback state of the service
     */
    var currentPlaybackState = PlaybackState.PREPARING
        protected set

    protected var currentPlaylistItem: I? = null

    /**
     * Performs the functionality to seek the current media item
     * to the specified position.  This should only be called directly
     * when performing the initial setup of playback position.  For
     * normal seeking process use the [.performSeekStarted] in
     * conjunction with [.performSeekEnded]
     */
    protected var seekToPosition: Long = -1

    protected var pausedForSeek = false
    protected var playingBeforeSeek = false
    protected var startPaused = false
    protected var pausedForFocusLoss = false

    var serviceListener: ServiceListener<I>? = null

    protected var mediaControlsHelper: MediaControlsHelper? = null

    protected var foregroundSetup: Boolean = false
    protected var notificationSetup: Boolean = false

    protected lateinit var notificationProvider: PlaylistNotificationProvider
    protected lateinit var mediaSessionProvider: MediaSessionProvider

    protected val notificationManager: NotificationManager by lazy {
        baseContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    protected val mediaInfo = MediaInfo()

    protected abstract val imageProvider: ImageProvider<I>

    /**
     * Retrieves the ID to use for the notification and registering this
     * service as Foreground when media is playing
     */
    protected open val notificationId: Int
    get() = R.id.playlistcore_default_notification_id

    /**
     * Returns the PendingIntent to use when the playback notification is clicked.
     * This is called when the playback is started initially to setup the notification
     * and the service as Foreground.
     */
    protected abstract val notificationClickPendingIntent: PendingIntent

    /**
     * Requests the service be transferred to the foreground, initializing the
     * RemoteView and Notification helpers for playback control.
     */
    protected fun setupAsForeground() {
        //Sets up the Notifications
        mediaInfo.showNotifications = true
        mediaInfo.notificationId = notificationId
        mediaInfo.appIcon = imageProvider.notificationIconRes

        //Starts the service as the foreground audio player
        notificationSetup = true
        setupForeground()
    }

    /**
     * Sets up the service as a Foreground service only if we aren't already registered as such
     */
    protected fun setupForeground() {
        if (!notificationSetup || foregroundSetup) {
            return
        }

        foregroundSetup = true
        startForeground(notificationId, notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), javaClass))
    }

    /**
     * If the service is registered as a foreground service then it will be unregistered
     * as such without removing the notification
     */
    protected fun stopForeground() {
        if (foregroundSetup) {
            foregroundSetup = false
            stopForeground(false)
        }
    }

    /**
     * Retrieves the volume level to use when audio focus has been temporarily
     * lost due to a higher importance notification.  The media will continue
     * playback but be reduced to the specified volume for the duration of the
     * notification.  This usually happens when receiving an alert for MMS, EMail,
     * etc
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    protected open val audioDuckVolume: Float
    get() = 0.1F

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
     * are the bits returned by [.onStartCommand] and can be
     * one of the [.START_CONTINUATION_MASK] values
     */
    @ServiceContinuationMethod
    val serviceContinuationMethod: Int
        get() = Service.START_NOT_STICKY

    /**
     * Used to determine if the device is connected to a network that has
     * internet access.  This is used in conjunction with [.isDownloaded]
     * to determine what items in the playlist manager, specified with [.getPlaylistManager], can be
     * played
     */
    protected val isNetworkAvailable: Boolean
        get() = true

    open fun updateMediaControls() {
        if (currentPlaylistItem == null || !notificationSetup) {
            return
        }

        updateMediaInfo()
        mediaSessionProvider.update(mediaInfo)
        mediaControlsHelper?.update(mediaInfo, mediaSessionProvider.get())

        // Updates the notification
        notificationManager.notify(mediaInfo.notificationId, notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), javaClass))
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")

        onServiceCreate()
    }

    /**
     * Stops the current media in playback and releases all
     * held resources.
     */
    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "Service Destroyed")
        setPlaybackState(PlaybackState.STOPPED)

        relaxResources(true)
        playlistManager.unRegisterService()

        audioFocusHelper?.let {
            it.setAudioFocusCallback(null)
            audioFocusHelper = null
        }

        mediaControlsHelper = null
    }

    /**
     * Handles the intents posted by the [BasePlaylistManager] through
     * the `invoke*` methods.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action ?: return serviceContinuationMethod

        if (RemoteActions.ACTION_START_SERVICE == intent.action) {
            seekToPosition = intent.getLongExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, -1)
            startPaused = intent.getBooleanExtra(RemoteActions.ACTION_EXTRA_START_PAUSED, false)

            startItemPlayback()
        } else {
            handleRemoteAction(intent.action, intent.extras)
        }

        return serviceContinuationMethod
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        onDestroy()
    }

    /**
     * Updates the playback state and volume based on the
     * previous state held before [.onAudioFocusLost]
     * was called.
     */
    override fun onAudioFocusGained(): Boolean {
        if (currentMediaPlayer?.handlesOwnAudioFocus ?: false) {
            return false
        }

        //Returns the audio to the previous playback state and volume
        if (!isPlaying && pausedForFocusLoss) {
            pausedForFocusLoss = false
            performPlay()
        } else {
            setVolume(1.0f, 1.0f) //reset the audio volume
        }

        return true
    }

    /**
     * Audio focus is lost either temporarily or permanently due
     * to external changes such as notification sounds and
     * phone calls.  When focus is lost temporarily, the
     * audio volume will decrease for the duration of the focus
     * loss (returned to normal in [.onAudioFocusGained].
     * If the focus is lost permanently then the audio will pause
     * playback and attempt to resume when [.onAudioFocusGained]
     * is called.
     */
    override fun onAudioFocusLost(canDuckAudio: Boolean): Boolean {
        if (currentMediaPlayer?.handlesOwnAudioFocus ?: false) {
            return false
        }

        //Either pauses or reduces the volume of the audio in playback
        if (!canDuckAudio) {
            if (isPlaying) {
                pausedForFocusLoss = true
                performPause()
            }
        } else {
            setVolume(audioDuckVolume, audioDuckVolume)
        }

        return true
    }

    /**
     * When the current media progress is updated we call through the
     * [BasePlaylistManager] to inform any listeners of the change
     */
    override fun onProgressUpdated(mediaProgress: MediaProgress): Boolean {
        currentMediaProgress = mediaProgress
        return playlistManager.onProgressUpdated(mediaProgress)
    }

    /**
     * Retrieves the current item change event which represents any media item changes.
     * This is intended as a utility method for initializing, or returning to, a media
     * playback UI.  In order to get the changed events you will need to register for
     * callbacks through [BasePlaylistManager.registerPlaylistListener]

     * @return The current PlaylistItem Changed event
     */
    val currentItemChange: PlaylistItemChange<I>
        get() {
            val hasNext = playlistManager.isNextAvailable
            val hasPrevious = playlistManager.isPreviousAvailable

            return PlaylistItemChange(currentPlaylistItem, hasPrevious, hasNext)
        }

    /**
     * Used to perform the onCreate functionality when the service is actually created.  This
     * should be overridden instead of [.onCreate] due to a bug in some Samsung devices
     * where [.onStartCommand] will get called before [.onCreate]
     */
    protected open fun onServiceCreate() {
        mediaProgressPoll.progressListener = this
        audioFocusHelper = AudioFocusHelper(applicationContext)
        audioFocusHelper?.setAudioFocusCallback(this)

        //Attempts to obtain the wifi lock only if the manifest has requested the permission
        if (packageManager.checkPermission(Manifest.permission.WAKE_LOCK, packageName) == PackageManager.PERMISSION_GRANTED) {
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, "mcLock")
            wifiLock?.setReferenceCounted(false)
        } else {
            Log.e(TAG, "Unable to acquire WAKE_LOCK due to missing manifest permission")
        }

        playlistManager.registerService(this)

        notificationProvider = DefaultPlaylistNotificationProvider(applicationContext)
        mediaSessionProvider = DefaultMediaSessionProvider(applicationContext, javaClass)
        mediaControlsHelper = MediaControlsHelper(applicationContext)
    }

    /**
     * A generic method to determine if media is currently playing.  This is
     * used to determine the playback state for the notification.
     *
     * @return True if media is currently playing
     */
    protected val isPlaying: Boolean
        get() = currentMediaPlayer?.isPlaying ?: false

    protected val isLoading: Boolean
        get() {
            return currentPlaybackState == PlaybackState.RETRIEVING ||
                    currentPlaybackState == PlaybackState.PREPARING ||
                    currentPlaybackState == PlaybackState.SEEKING
        }

    /**
     * Performs the functionality to pause and/or resume
     * the media playback.  This is called through an intent
     * with the [RemoteActions.ACTION_PLAY_PAUSE], through
     * [BasePlaylistManager.invokePausePlay]
     */
    protected fun performPlayPause() {
        if (isPlaying) {
            performPause()
        } else {
            performPlay()
        }
    }

    /**
     * Performs the functionality to seek to the previous media
     * item.  This is called through an intent
     * with the [RemoteActions.ACTION_PREVIOUS], through
     * [BasePlaylistManager.invokePrevious]
     */
    protected fun performPrevious() {
        seekToPosition = 0
        startPaused = !isPlaying

        playlistManager.previous()
        startItemPlayback()
    }

    /**
     * Performs the functionality to seek to the next media
     * item.  This is called through an intent
     * with the [RemoteActions.ACTION_NEXT], through
     * [BasePlaylistManager.invokeNext]
     */
    protected fun performNext() {
        seekToPosition = 0
        startPaused = !isPlaying

        playlistManager.next()
        startItemPlayback()
    }

    /**
     * Performs the functionality for when a media item
     * has finished playback.
     */
    open fun performOnMediaCompletion() {
        // Handles moving to the next playable item
        performNext()
        startPaused = false
    }

    /**
     * Called when the playback of the specified media item has
     * encountered an error.
     */
    open fun performOnMediaError() {
        setPlaybackState(PlaybackState.ERROR)

        stopForeground()
        updateWiFiLock(false)
        mediaProgressPoll.stop()

        abandonAudioFocus()
    }

    open fun performOnSeekComplete() {
        if (pausedForSeek || playingBeforeSeek) {
            performPlay()
            pausedForSeek = false
            playingBeforeSeek = false
        } else {
            performPause()
        }
    }

    /**
     * Performs the functionality to start a seek for the current
     * media item.  This is called through an intent
     * with the [RemoteActions.ACTION_SEEK_STARTED], through
     * [BasePlaylistManager.invokeSeekStarted]
     */
    protected fun performSeekStarted() {
        if (isPlaying) {
            pausedForSeek = true
            performPause()
        }
    }

    /**
     * Performs the functionality to end a seek for the current
     * media item.  This is called through an intent
     * with the [RemoteActions.ACTION_SEEK_ENDED], through
     * [BasePlaylistManager.invokeSeekEnded]
     */
    protected fun performSeekEnded(newPosition: Long) {
        performSeek(newPosition)
    }

    /**
     * A helper method that allows us to update the audio volume of the media
     * in playback when AudioFocus is lost or gained and we can dim instead
     * of pausing playback.

     * @param left The left channels audio volume
     * *
     * @param right The right channels audio volume
     */
    protected fun setVolume(left: Float, right: Float) {
        currentMediaPlayer?.setVolume(left, right)
    }

    /**
     * Informs the callbacks specified with [BasePlaylistManager.registerPlaylistListener]
     * that the current playlist item has changed.
     */
    protected fun postPlaylistItemChanged() {
        val hasNext = playlistManager.isNextAvailable
        val hasPrevious = playlistManager.isPreviousAvailable
        playlistManager.onPlaylistItemChanged(currentPlaylistItem, hasNext, hasPrevious)
    }

    /**
     * Informs the callbacks specified with [BasePlaylistManager.registerPlaylistListener]
     * that the current media state has changed.
     */
    protected fun postPlaybackStateChanged() {
        playlistManager.onPlaybackStateChanged(currentPlaybackState)
    }

    /**
     * Performs the functionality to stop the media playback.  This will perform any cleanup
     * and stop the service.
     */
    protected fun performStop() {
        setPlaybackState(PlaybackState.STOPPED)
        currentPlaylistItem?.let {
            serviceListener?.onMediaStopped(it)
        }

        // let go of all resources
        relaxResources(true)

        playlistManager.reset()
        stopSelf()
    }

    /**
     * Performs the functionality to seek the current media item
     * to the specified position.  This should only be called directly
     * when performing the initial setup of playback position.  For
     * normal seeking process use the [.performSeekStarted] in
     * conjunction with [.performSeekEnded]
     *
     * @param position The position to seek to in milliseconds
     * @param updatePlaybackState True if the playback state should be updated
     */
    @JvmOverloads
    protected fun performSeek(position: Long, updatePlaybackState: Boolean = true) {
        playingBeforeSeek = isPlaying
        currentMediaPlayer?.seekTo(position)

        if (updatePlaybackState) {
            setPlaybackState(PlaybackState.SEEKING)
        }
    }

    /**
     * Performs the functionality to actually pause the current media
     * playback.
     */
    protected fun performPause() {
        if (isPlaying) {
            currentMediaPlayer?.pause()
        }

        mediaProgressPoll.stop()
        setPlaybackState(PlaybackState.PAUSED)
        stopForeground()

        abandonAudioFocus()
        updateMediaControls()
    }

    /**
     * Performs the functionality to actually play the current media
     * item.
     */
    protected fun performPlay() {
        if (!isPlaying) {
            currentMediaPlayer?.play()
        }

        mediaProgressPoll.start()
        setPlaybackState(PlaybackState.PLAYING)
        setupForeground()

        requestAudioFocus()
        updateMediaControls()
    }

    /**
     * Starts the actual media playback
     *
     * ***NOTE:*** In order to play videos you will need to specify the
     * VideoPlayerApi with [BasePlaylistManager.setVideoPlayer]
     */
    protected fun startItemPlayback() {
        serviceListener?.onMediaPlaybackEnded()
        getNextPlayableItem()

        val item = currentPlaylistItem
        currentMediaPlayer = item?.let { getMediaPlayerForItem(it) }

        mediaItemChanged(item)
        if (play(currentMediaPlayer, item)) {
            return
        }

        //If the playback wasn't handled, attempt to seek to the next playable item, otherwise stop the service
        if (playlistManager.isNextAvailable) {
            performNext()
        } else {
            performStop()
        }
    }

    protected fun getMediaPlayerForItem(item: I): MediaPlayerApi<I>? {
        // TODO: should we prioritize the current player or ones higher in the list?
        if (currentMediaPlayer?.handlesItem(item) ?: false) {
            return currentMediaPlayer
        }

        return mediaPlayers.firstOrNull { it.handlesItem(item) }
    }

    /**
     * Starts the actual playback of the specified audio item.
     *
     * @return True if the item playback was correctly handled
     */
    protected fun play(mediaPlayer: MediaPlayerApi<I>?, item: I?): Boolean {
        if (mediaPlayer == null || item == null) {
            return false
        }

        initializeMediaPlayer(mediaPlayer)
        requestAudioFocus()

        mediaPlayer.playItem(item)

        setPlaybackState(PlaybackState.PREPARING)
        setupAsForeground()

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        updateWiFiLock(!(currentPlaylistItem?.downloaded ?: true))
        return true
    }

    /**
     * Reconfigures the mediaPlayerApi according to audio focus settings and starts/restarts it. This
     * method starts/restarts the mediaPlayerApi respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * mediaPlayerApi paused or set it to a low volume, depending on what is allowed by the
     * current focus settings.
     */
    open fun startMediaPlayer() {
        if (!(currentMediaPlayer?.handlesOwnAudioFocus ?: true)) {
            if (audioFocusHelper == null || audioFocusHelper!!.currentAudioFocus == AudioFocusHelper.Focus.NO_FOCUS_NO_DUCK) {
                // If we don't have audio focus and can't duck we have to pause, even if state is playing
                // Be we stay in the playing state so we know we have to resume playback once we get the focus back.
                if (isPlaying) {
                    pausedForFocusLoss = true
                    performPause()
                    serviceListener?.onMediaPlaybackEnded(currentPlaylistItem!!, currentMediaPlayer!!.currentPosition, currentMediaPlayer!!.duration)
                }

                return
            } else if (audioFocusHelper!!.currentAudioFocus == AudioFocusHelper.Focus.NO_FOCUS_CAN_DUCK) {
                setVolume(audioDuckVolume, audioDuckVolume)
            } else {
                setVolume(1.0f, 1.0f)
            }
        }

        //Seek to the correct position
        val seekRequested = seekToPosition > 0
        if (seekRequested) {
            performSeek(seekToPosition, false)
            seekToPosition = -1
        }

        //Start the playback only if requested, otherwise upate the state to paused
        mediaProgressPoll.start()
        if (!isPlaying && !startPaused) {
            pausedForSeek = seekRequested
            performPlay()
            serviceListener?.onMediaPlaybackStarted(currentPlaylistItem!!, currentMediaPlayer!!.currentPosition, currentMediaPlayer!!.duration)
        } else {
            setPlaybackState(PlaybackState.PAUSED)
        }
    }

    /**
     * Requests the audio focus
     */
    protected fun requestAudioFocus(): Boolean {
        return currentMediaPlayer?.handlesOwnAudioFocus ?: false || audioFocusHelper?.requestFocus() ?: false
    }

    /**
     * Requests the audio focus to be abandoned
     */
    protected fun abandonAudioFocus(): Boolean {
        return currentMediaPlayer?.handlesOwnAudioFocus ?: false || audioFocusHelper?.abandonFocus() ?: false
    }

    /**
     * Releases resources used by the service for playback. This includes the "foreground service"
     * status and notification, the wake locks, and the audioPlayer if requested
     *
     * @param releaseAudioPlayer True if the audioPlayer should be released
     */
    protected open fun relaxResources(releaseAudioPlayer: Boolean) {
        mediaProgressPoll.release()

        if (releaseAudioPlayer) {
            currentMediaPlayer?.let {
                it.reset()
                it.release()
                currentMediaPlayer = null
            }

            playlistManager.currentPosition = Integer.MAX_VALUE
        }

        abandonAudioFocus()
        updateWiFiLock(false)

        stopForeground(true)

        foregroundSetup = false
        notificationSetup = false

        notificationManager.cancel(notificationId)
        mediaSessionProvider.get().release()
    }

    /**
     * Acquires or releases the WiFi lock

     * @param acquire True if the WiFi lock should be acquired, false to release
     */
    protected fun updateWiFiLock(acquire: Boolean) {
        wifiLock?.let {
            if (acquire && !it.isHeld) {
                it.acquire()
            } else if (!acquire && it.isHeld) {
                it.release()
            }
        }
    }

    /**
     * Updates the current PlaybackState and informs any listening classes.

     * @param state The new PlaybackState
     */
    protected open fun setPlaybackState(state: PlaybackState) {
        currentPlaybackState = state
        postPlaybackStateChanged()
    }

    /**
     * Iterates through the playList, starting with the current item, until we reach an item we can play.
     * Normally this will be the current item, however if they don't have network then
     * it will be the next downloaded item.
     */
    protected open fun getNextPlayableItem(): I? {
        currentPlaylistItem = playlistManager.currentItem
        currentPlaylistItem ?: return null

        // TODO: if we can't play an item should we inform the listener as to why exactly? or just say "eh, we can't play `this` item"
        var item = currentPlaylistItem
        while (item != null && !isPlayable(item)) {
            item = playlistManager.next()
        }

        //If we are unable to get a next playable item, post a network error
        item ?: serviceListener?.onNoNonNetworkItemsAvailable()
        currentPlaylistItem = item

        return currentPlaylistItem
    }

    protected open fun isPlayable(item: I): Boolean {
        return (isNetworkAvailable || item.downloaded) && getMediaPlayerForItem(item) != null
    }

    /**
     * Called when the current media item has changed, this will update the notification and
     * media control values.
     */
    protected open fun mediaItemChanged(item: I?) {
        //Validates that the currentPlaylistItem is for the currentItem
        if (!playlistManager.isPlayingItem(item)) {
            Log.d(TAG, "forcing currentPlaylistItem update")
            currentPlaylistItem = playlistManager.currentItem
        }

        item?.let {
            imageProvider.updateImages(it)
        }

        postPlaylistItemChanged()
    }

    /**
     * Handles the remote actions from the big notification and media controls
     * to control the media playback
     *
     * @param action The action from the intent to handle
     * @param extras The extras packaged with the intent associated with the action
     * @return True if the remote action was handled
     */
    protected fun handleRemoteAction(action: String?, extras: Bundle?): Boolean {
        if (action == null || action.isEmpty()) {
            return false
        }

        when (action) {
            RemoteActions.ACTION_PLAY_PAUSE -> performPlayPause()
            RemoteActions.ACTION_NEXT -> performNext()
            RemoteActions.ACTION_PREVIOUS -> performPrevious()
            RemoteActions.ACTION_STOP -> performStop()
            RemoteActions.ACTION_SEEK_STARTED -> performSeekStarted()
            RemoteActions.ACTION_SEEK_ENDED -> performSeekEnded(extras?.getLong(RemoteActions.ACTION_EXTRA_SEEK_POSITION, 0) ?: 0)

            else -> return false
        }

        return true
    }

    protected fun initializeMediaPlayer(mediaPlayer: MediaPlayerApi<I>) {
        mediaPlayer.apply {
            stop()
            reset()
            setMediaStatusListener(mediaListener)
        }

        mediaProgressPoll.update(mediaPlayer)
        mediaProgressPoll.reset()
    }

    protected open fun updateMediaInfo() {
        // Generate the notification state
        mediaInfo.mediaState.isPlaying = isPlaying
        mediaInfo.mediaState.isLoading = isLoading
        mediaInfo.mediaState.isNextEnabled = playlistManager.isNextAvailable
        mediaInfo.mediaState.isPreviousEnabled = playlistManager.isPreviousAvailable

        // Updates the notification information
        mediaInfo.playlistItem = currentPlaylistItem
        mediaInfo.pendingIntent = notificationClickPendingIntent
        mediaInfo.artwork = imageProvider.remoteViewArtwork
        mediaInfo.largeNotificationIcon = imageProvider.largeNotificationImage
    }
}
