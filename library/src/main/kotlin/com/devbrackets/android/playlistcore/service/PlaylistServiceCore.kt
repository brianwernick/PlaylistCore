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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.annotation.FloatRange
import android.support.annotation.IntRange
import android.util.Log
import com.devbrackets.android.playlistcore.annotation.ServiceContinuationMethod
import com.devbrackets.android.playlistcore.annotation.SupportedMediaType
import com.devbrackets.android.playlistcore.api.AudioPlayerApi
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.VideoPlayerApi
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.event.PlaylistItemChange
import com.devbrackets.android.playlistcore.helper.AudioFocusHelper
import com.devbrackets.android.playlistcore.listener.*
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager.Companion.AUDIO
import com.devbrackets.android.playlistcore.manager.IPlaylistItem
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
 */
abstract class PlaylistServiceCore<I : IPlaylistItem, M : BasePlaylistManager<I>> : Service(), AudioFocusHelper.AudioFocusCallback, ProgressListener {
    companion object {
        private val TAG = "PlaylistServiceCore"
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

    protected //Null if the WAKE_LOCK permission wasn't requested
    var wifiLock: WifiManager.WifiLock? = null
    protected var audioFocusHelper: AudioFocusHelper? = null

    protected var audioPlayer: AudioPlayerApi? = null
    protected var mediaProgressPoll = MediaProgressPoll()
    protected var mediaListener = MediaListener()
    /**
     * Retrieves the current playback progress.
     *
     * @return The current playback progress
     */
    var currentMediaProgress = MediaProgress(0, 0, 0)
        protected set

    /**
     * Retrieves the current playback state of the service.
     *
     * @return The current playback state
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

     * @param position The position to seek to in milliseconds
     */
    protected var seekToPosition: Long = -1

    protected var pausedForSeek = false
    protected var playingBeforeSeek = false
    protected var startPaused = false
    protected var pausedForFocusLoss = false
    protected var onCreateCalled = false

    protected var workaroundIntent: Intent? = null

    /**
     * Retrieves a new instance of the [AudioPlayerApi]. This will only
     * be called the first time an Audio Player is needed; afterwards a cached
     * instance will be used.
     *
     * @return The [AudioPlayerApi] to use for playback
     */
    protected abstract val newAudioPlayer: AudioPlayerApi

    protected abstract fun setupAsForeground()
    protected abstract fun setupForeground()
    protected abstract fun stopForeground()

    /**
     * Retrieves the volume level to use when audio focus has been temporarily
     * lost due to a higher importance notification.  The media will continue
     * playback but be reduced to the specified volume for the duration of the
     * notification.  This usually happens when receiving an alert for MMS, EMail,
     * etc.
     *
     * @return The volume level to use when a temporary notification sounds. [0.0 - 1.0]
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    protected abstract val audioDuckVolume: Float

    /**
     * Links the [BasePlaylistManager] that contains the information for playback
     * to this service.
     *
     * NOTE: this is only used for retrieving information, it isn't used to register notifications
     * for playlist changes, however as long as the change isn't breaking (e.g. cleared playlist)
     * then nothing additional needs to be performed.
     *
     * @return The [BasePlaylistManager] containing the playback information
     */
    protected abstract val playlistManager: M

    /**
     * Retrieves the continuity bits associated with the service.  These
     * are the bits returned by [.onStartCommand] and can be
     * one of the [.START_CONTINUATION_MASK] values.
     *
     * @return The continuity bits for the service [default: [.START_NOT_STICKY]]
     */
    val serviceContinuationMethod: Int
        @ServiceContinuationMethod
        get() = Service.START_NOT_STICKY

    /**
     * Used to determine if the device is connected to a network that has
     * internet access.  This is used in conjunction with [.isDownloaded]
     * to determine what items in the playlist manager, specified with [.getPlaylistManager], can be
     * played.
     *
     * @return True if the device currently has internet connectivity
     */
    protected val isNetworkAvailable: Boolean
        get() = true

    /**
     * Used to determine if the specified playlistItem has been downloaded.  If this is true
     * then the downloaded copy will be used instead, and no network wakelock will be acquired.
     *
     * @param playlistItem The playlist item to determine if it is downloaded.
     * @return True if the specified playlistItem is downloaded. [default: false]
     */
    protected fun isDownloaded(playlistItem: I): Boolean {
        return false
    }

    /**
     * Called when the [.performStop] has been called.
     *
     * @param playlistItem The playlist item that has been stopped
     */
    protected fun onMediaStopped(playlistItem: I) {
        //Purposefully left blank
    }

    /**
     * Called when a current media item has ended playback.  This is called when we
     * are unable to play an item.
     *
     * @param playlistItem The PlaylistItem that has ended
     * @param currentPosition The position the playlist item ended at
     * @param duration The duration of the PlaylistItem
     */
    protected fun onMediaPlaybackEnded(playlistItem: I, currentPosition: Long, duration: Long) {
        //Purposefully left blank
    }

    /**
     * Called when a media item has started playback.
     *
     * @param playlistItem The PlaylistItem that has started playback
     * @param currentPosition The position the playback has started at
     * @param duration The duration of the PlaylistItem
     */
    protected fun onMediaPlaybackStarted(playlistItem: I, currentPosition: Long, duration: Long) {
        //Purposefully left blank
    }

    /**
     * Called when the service is unable to seek to the next playable item when
     * no network is available.
     */
    protected fun onNoNonNetworkItemsAvailable() {
        //Purposefully left blank
    }

    /**
     * Called when a media item in playback has ended
     */
    protected fun onMediaPlaybackEnded() {
        //Purposefully left blank
    }

    //todo
    protected open fun updateMediaControls() {

    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        //Part of a workaround for some Samsung devices (see onStartCommand)
        if (onCreateCalled) {
            return
        }

        onCreateCalled = true
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

        onCreateCalled = false
    }

    /**
     * Handles the intents posted by the [BasePlaylistManager] through
     * the `invoke*` methods.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            return serviceContinuationMethod
        }

        //This is a workaround for an issue on the Samsung Galaxy S3 (4.4.2) where the onStartCommand will occasionally get called before onCreate
        if (!onCreateCalled) {
            Log.d(TAG, "Starting Samsung workaround")
            workaroundIntent = intent
            onCreate()
            return serviceContinuationMethod
        }

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
        if (!handleVideoAudioFocus() && currentItemIsType(BasePlaylistManager.VIDEO)) {
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
        if (!handleVideoAudioFocus() && currentItemIsType(BasePlaylistManager.VIDEO)) {
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
     * where [.onStartCommand] will get called before [.onCreate].
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
            Log.w(TAG, "Unable to acquire WAKE_LOCK due to missing manifest permission")
        }

        playlistManager.registerService(this)

        //Another part of the workaround for some Samsung devices
        workaroundIntent?.let {
            startService(it)
            workaroundIntent = null
        }
    }

    /**
     * A generic method to determine if media is currently playing.  This is
     * used to determine the playback state for the notification.
     *
     * @return True if media is currently playing
     */
    protected val isPlaying: Boolean
        get() {
            if (currentItemIsType(BasePlaylistManager.AUDIO)) {
                return audioPlayer != null && audioPlayer!!.isPlaying
            } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
                return playlistManager.getVideoPlayer() != null && playlistManager.getVideoPlayer()!!.isPlaying
            }

            return false
        }

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
     * Performs the functionality to repeat the current
     * media item in playback.  This is called through an
     * intent with the [RemoteActions.ACTION_REPEAT],
     * through [BasePlaylistManager.invokeRepeat]
     */
    protected fun performRepeat() {
        //Left for the extending class to implement
    }

    /**
     * Performs the functionality to repeat the current
     * media item in playback.  This is called through an
     * intent with the [RemoteActions.ACTION_SHUFFLE],
     * through [BasePlaylistManager.invokeShuffle]
     */
    protected fun performShuffle() {
        //Left for the extending class to implement
    }

    /**
     * Performs the functionality for when a media item
     * has finished playback.
     */
    protected open fun performOnMediaCompletion() {
        //Left for the extending class to implement
    }

    /**
     * Called when the playback of the specified media item has
     * encountered an error.
     */
    protected fun performOnMediaError() {
        setPlaybackState(PlaybackState.ERROR)

        stopForeground()
        updateWiFiLock(false)
        mediaProgressPoll.stop()

        abandonAudioFocus()
    }

    /**
     * Performs the functionality to start a seek for the current
     * media item.  This is called through an intent
     * with the [RemoteActions.ACTION_SEEK_STARTED], through
     * [BasePlaylistManager.invokeSeekStarted]
     */
    protected fun performSeekStarted() {
        var isPlaying = false

        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            isPlaying = audioPlayer != null && audioPlayer!!.isPlaying
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            val videoPlayer = playlistManager.getVideoPlayer()
            isPlaying = videoPlayer != null && videoPlayer.isPlaying
        }

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
        if (currentItemIsType(BasePlaylistManager.AUDIO) && audioPlayer != null) {
            audioPlayer?.setVolume(left, right)
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            val videoPlayerApi = playlistManager.getVideoPlayer()
            videoPlayerApi?.setVolume(1.0f, 1.0f)
        }
    }

    /**
     * Sets the media type to be allowed in the playlist.  If the type is changed
     * during media playback, the current item will be compared against the new
     * allowed type.  If the current item type and the new type are not compatible
     * then the playback will be seeked to the next valid item.

     * @param newType The new allowed media type
     */
    protected fun updateAllowedMediaType(@SupportedMediaType newType: Int) {
        //We seek through the items until an allowed one is reached, or none is reached and the service is stopped.
        if (currentPlaylistItem != null && newType and currentPlaylistItem!!.mediaType == 0) {
            performNext()
        }
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
     * Specifies if the VideoView audio focus should be handled by the
     * PlaylistService.  This is `false` by default because
     * the Android VideoView handles it's own audio focus, which would
     * cause the video to never play on it's own if this were enabled.
     *
     * @return True if the PlaylistService should handle audio focus for videos
     */
    protected fun handleVideoAudioFocus(): Boolean {
        return false
    }

    /**
     * Performs the functionality to stop the media playback.  This will perform any cleanup
     * and stop the service.
     */
    protected fun performStop() {
        setPlaybackState(PlaybackState.STOPPED)
        currentPlaylistItem?.let { onMediaStopped(it) }

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
    @JvmOverloads protected fun performSeek(position: Long, updatePlaybackState: Boolean = true) {
        var isPlaying = false

        if (currentItemIsType(BasePlaylistManager.AUDIO) && audioPlayer != null) {
            isPlaying = audioPlayer?.isPlaying ?: false
            audioPlayer?.seekTo(position)
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            playlistManager.getVideoPlayer()?.let {
                isPlaying = it.isPlaying
                it.seekTo(position)
            }
        }

        playingBeforeSeek = isPlaying
        if (updatePlaybackState) {
            setPlaybackState(PlaybackState.SEEKING)
        }
    }

    /**
     * Performs the functionality to actually pause the current media
     * playback.
     */
    protected fun performPause() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            if (audioPlayer?.isPlaying ?: false) {
                audioPlayer?.pause()
            }
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            playlistManager.getVideoPlayer()?.let {
                if (it.isPlaying) {
                    it.pause()
                }
            }
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
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            audioPlayer?.let {
                if (!it.isPlaying) {
                    it.play()
                }
            }
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            playlistManager.getVideoPlayer()?.let {
                if (!it.isPlaying) {
                    it.play()
                }
            }
        }

        mediaProgressPoll.start()
        setPlaybackState(PlaybackState.PLAYING)
        setupForeground()

        requestAudioFocus()
        updateMediaControls()
    }

    /**
     * Determines if the current media item is of the passed type.  This is specified
     * with [IPlaylistItem.getMediaType]
     *
     * @return True if the current media item is of the same passed type
     */
    protected fun currentItemIsType(@SupportedMediaType type: Int): Boolean {
        return (currentPlaylistItem?.mediaType ?: 0) and type != 0
    }

    /**
     * Starts the actual media playback
     *
     * ***NOTE:*** In order to play videos you will need to specify the
     * VideoPlayerApi with [BasePlaylistManager.setVideoPlayer]
     */
    protected fun startItemPlayback() {
        onMediaPlaybackEnded()
        seekToNextPlayableItem()
        mediaItemChanged()

        //Performs the playback for the correct media type
        var playbackHandled = false
        mediaListener.resetRetryCount()

        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            playbackHandled = playAudioItem()
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            playbackHandled = playVideoItem()
        } else if (currentPlaylistItem != null && currentPlaylistItem!!.mediaType != 0) {
            playbackHandled = playOtherItem()
        }

        if (playbackHandled) {
            return
        }

        //If the playback wasn't handled, attempt to seek to the next playable item, otherwise stop the service
        if (playlistManager.isNextAvailable) {
            performNext()
        } else {
            performStop()
        }
    }

    /**
     * Starts the actual playback of the specified audio item.
     *
     * @return True if the item playback was correctly handled
     */
    protected fun playAudioItem(): Boolean {
        stopVideoPlayback()
        initializeAudioPlayer()
        requestAudioFocus()

        mediaProgressPoll.update(audioPlayer)
        mediaProgressPoll.reset()

        val isItemDownloaded = isDownloaded(currentPlaylistItem!!)

        audioPlayer?.setDataSource(this, Uri.parse(if (isItemDownloaded) currentPlaylistItem!!.downloadedMediaUri else currentPlaylistItem!!.mediaUrl))

        setPlaybackState(PlaybackState.PREPARING)
        setupAsForeground()

        audioPlayer?.prepareAsync()

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        updateWiFiLock(!isItemDownloaded)
        return true
    }

    /**
     * Starts the actual playback of the specified video item.
     *
     * @return True if the item playback was correctly handled
     */
    protected fun playVideoItem(): Boolean {
        stopAudioPlayback()
        initializeVideoPlayer()
        requestAudioFocus()

        val videoPlayer = playlistManager.getVideoPlayer() ?: return false

        mediaProgressPoll.update(videoPlayer)
        mediaProgressPoll.reset()

        val isItemDownloaded = isDownloaded(currentPlaylistItem!!)

        videoPlayer.setDataSource(Uri.parse(if (isItemDownloaded) currentPlaylistItem!!.downloadedMediaUri else currentPlaylistItem!!.mediaUrl))

        setPlaybackState(PlaybackState.PREPARING)
        setupAsForeground()

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        updateWiFiLock(!isItemDownloaded)
        return true
    }

    /**
     * Starts the playback of the specified other item type.
     *
     * @return True if the item playback was correctly handled
     */
    protected fun playOtherItem(): Boolean {
        return false
    }

    /**
     * Stops the AudioPlayer from playing.
     */
    protected fun stopAudioPlayback() {
        audioPlayer?.let {
            it.stop()
            it.reset()
        }
    }

    /**
     * Stops the VideoView from playing if we have access to it.
     */
    protected fun stopVideoPlayback() {
        playlistManager.getVideoPlayer()?.let {
            it.stop()
            it.reset()
        }
    }

    /**
     * Starts the appropriate media playback based on the current item type.
     * If the current item is audio, then the playback will make sure to pay
     * attention to the current audio focus.
     */
    protected fun startMediaPlayer() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            startAudioPlayer()
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            startVideoPlayer()
        }
    }

    /**
     * Reconfigures audioPlayer according to audio focus settings and starts/restarts it. This
     * method starts/restarts the audioPlayer respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * audioPlayer paused or set it to a low volume, depending on what is allowed by the
     * current focus settings.
     */
    protected fun startAudioPlayer() {
        audioPlayer?.let {
            startMediaPlayer(it)
        }
    }

    /**
     * Reconfigures the videoPlayer according to audio focus settings and starts/restarts it. This
     * method starts/restarts the videoPlayer respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * videoPlayer paused or set it to a low volume, depending on what is allowed by the
     * current focus settings.
     */
    protected fun startVideoPlayer() {
        playlistManager.getVideoPlayer()?.let {
            startMediaPlayer(it)
        }
    }

    /**
     * Reconfigures the mediaPlayerApi according to audio focus settings and starts/restarts it. This
     * method starts/restarts the mediaPlayerApi respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * mediaPlayerApi paused or set it to a low volume, depending on what is allowed by the
     * current focus settings.
     */
    protected fun startMediaPlayer(mediaPlayerApi: MediaPlayerApi) {
        if (handleVideoAudioFocus() || mediaPlayerApi !is VideoPlayerApi) {
            if (audioFocusHelper == null || audioFocusHelper!!.currentAudioFocus === AudioFocusHelper.Focus.NO_FOCUS_NO_DUCK) {
                // If we don't have audio focus and can't duck we have to pause, even if state is playing
                // Be we stay in the playing state so we know we have to resume playback once we get the focus back.
                if (isPlaying) {
                    pausedForFocusLoss = true
                    performPause()
                    onMediaPlaybackEnded(currentPlaylistItem!!, mediaPlayerApi.currentPosition, mediaPlayerApi.duration)
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
            onMediaPlaybackStarted(currentPlaylistItem!!, mediaPlayerApi.currentPosition, mediaPlayerApi.duration)
        } else {
            setPlaybackState(PlaybackState.PAUSED)
        }
    }

    /**
     * Requests the audio focus
     */
    protected fun requestAudioFocus(): Boolean {
        if (!handleVideoAudioFocus() && currentItemIsType(BasePlaylistManager.VIDEO)) {
            return false
        }

        return audioFocusHelper?.requestFocus() ?: false
    }

    /**
     * Requests the audio focus to be abandoned
     */
    protected fun abandonAudioFocus(): Boolean {
        if (!handleVideoAudioFocus() && currentItemIsType(BasePlaylistManager.VIDEO)) {
            return false
        }

        return audioFocusHelper?.abandonFocus() ?: false
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
            audioPlayer?.let {
                it.reset()
                it.release()
                audioPlayer = null
            }

            playlistManager.currentPosition = Integer.MAX_VALUE
        }

        abandonAudioFocus()
        updateWiFiLock(false)
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
    protected fun setPlaybackState(state: PlaybackState) {
        currentPlaybackState = state
        postPlaybackStateChanged()
    }

    /**
     * Iterates through the playList, starting with the current item, until we reach an item we can play.
     * Normally this will be the current item, however if they don't have network then
     * it will be the next downloaded item.
     */
    protected fun seekToNextPlayableItem() {
        var currentItem = playlistManager.currentItem
        if (currentItem == null) {
            currentPlaylistItem = null
            return
        }

        //Only iterate through the list if we aren't connected to the internet
        if (!isNetworkAvailable) {
            while (currentItem != null && !isDownloaded(currentItem)) {
                currentItem = playlistManager.next()
            }
        }

        //If we are unable to get a next playable item, post a network error
        if (currentItem == null) {
            onNoNonNetworkItemsAvailable()
        }

        currentPlaylistItem = playlistManager.currentItem
    }

    /**
     * Called when the current media item has changed, this will update the notification and
     * media control values.
     */
    protected open fun mediaItemChanged() {
        //Validates that the currentPlaylistItem is for the currentItem
        if (!playlistManager.isPlayingItem(currentPlaylistItem)) {
            Log.d(TAG, "forcing currentPlaylistItem update")
            currentPlaylistItem = playlistManager.currentItem
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
            RemoteActions.ACTION_REPEAT -> performRepeat()
            RemoteActions.ACTION_SHUFFLE -> performShuffle()
            RemoteActions.ACTION_STOP -> performStop()
            RemoteActions.ACTION_SEEK_STARTED -> performSeekStarted()
            RemoteActions.ACTION_SEEK_ENDED -> performSeekEnded(extras?.getLong(RemoteActions.ACTION_EXTRA_SEEK_POSITION, 0) ?: 0)
            RemoteActions.ACTION_ALLOWED_TYPE_CHANGED -> updateAllowedMediaType(extras?.getInt(RemoteActions.ACTION_EXTRA_ALLOWED_TYPE) ?: AUDIO)

            else -> return false
        }

        return true
    }

    /**
     * Initializes the audio player.
     * If the audio player has already been initialized, then it will
     * be reset to prepare for the next playback item.
     */
    protected fun initializeAudioPlayer() {
        audioPlayer?.let {
            it.reset()
            return
        }

        audioPlayer = newAudioPlayer.apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setStreamType(AudioManager.STREAM_MUSIC)

            // Sets the listeners
            setOnMediaPreparedListener(mediaListener)
            setOnMediaCompletionListener(mediaListener)
            setOnMediaErrorListener(mediaListener)
            setOnMediaSeekCompletionListener(mediaListener)
            setOnMediaBufferUpdateListener(mediaListener)
        }
    }

    /**
     * Initializes the video players connection to this Service, including
     * adding media playback listeners.
     */
    protected fun initializeVideoPlayer() {
        val videoPlayer = playlistManager.getVideoPlayer() ?: return

        //Sets the listeners
        videoPlayer.setOnMediaPreparedListener(mediaListener)
        videoPlayer.setOnMediaCompletionListener(mediaListener)
        videoPlayer.setOnMediaErrorListener(mediaListener)
        videoPlayer.setOnMediaSeekCompletionListener(mediaListener)
        videoPlayer.setOnMediaBufferUpdateListener(mediaListener)
    }

    /**
     * A class to listen to the [MediaPlayerApi] events, and will
     * retry playback once if the media is audio when an error is encountered.
     * This is done to workaround an issue on older (pre 4.1)
     * devices where playback will fail due to a race condition
     * in the [MediaPlayer]
     */
    protected inner class MediaListener : OnMediaPreparedListener, OnMediaCompletionListener, OnMediaErrorListener, OnMediaSeekCompletionListener, OnMediaBufferUpdateListener {
        private val MAX_RETRY_COUNT = 1
        private var retryCount = 0

        override fun onPrepared(mediaPlayerApi: MediaPlayerApi) {
            retryCount = 0
            startMediaPlayer()
        }

        override fun onCompletion(mediaPlayerApi: MediaPlayerApi) {
            performOnMediaCompletion()
        }

        override fun onError(mediaPlayerApi: MediaPlayerApi): Boolean {
            if (!retryAudio()) {
                performOnMediaError()
            }

            return false
        }

        override fun onSeekComplete(mediaPlayerApi: MediaPlayerApi) {
            if (pausedForSeek || playingBeforeSeek) {
                performPlay()
                pausedForSeek = false
                playingBeforeSeek = false
            } else {
                performPause()
            }
        }

        override fun onBufferingUpdate(mediaPlayerApi: MediaPlayerApi, @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong()) percent: Int) {
            //Makes sure to update listeners of buffer updates even when playback is paused
            if (!mediaPlayerApi.isPlaying && currentMediaProgress.bufferPercent != percent) {
                currentMediaProgress.update(mediaPlayerApi.currentPosition, percent, mediaPlayerApi.duration)
                onProgressUpdated(currentMediaProgress)
            }
        }

        fun resetRetryCount() {
            retryCount = 0
        }

        /**
         * The retry count is a workaround for when the EMAudioPlayer will occasionally fail
         * to load valid content due to the MediaPlayer on pre 4.1 devices

         * @return True if a retry was started
         */
        fun retryAudio(): Boolean {
            if (currentItemIsType(BasePlaylistManager.AUDIO) && ++retryCount <= MAX_RETRY_COUNT) {
                Log.d(TAG, "Retrying audio playback.  Retry count: " + retryCount)
                playAudioItem()
                return true
            }

            return false
        }
    }
}
