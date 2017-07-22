package com.devbrackets.android.playlistcore.helper.playlist

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.util.Log
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.event.MediaProgress
import com.devbrackets.android.playlistcore.event.PlaylistItemChange
import com.devbrackets.android.playlistcore.helper.AudioFocusHelper
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper
import com.devbrackets.android.playlistcore.helper.SafeWifiLock
import com.devbrackets.android.playlistcore.helper.image.ImageProvider
import com.devbrackets.android.playlistcore.helper.mediasession.DefaultMediaSessionProvider
import com.devbrackets.android.playlistcore.helper.mediasession.MediaSessionProvider
import com.devbrackets.android.playlistcore.helper.notification.DefaultPlaylistNotificationProvider
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo
import com.devbrackets.android.playlistcore.helper.notification.PlaylistNotificationProvider
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.service.PlaybackState
import com.devbrackets.android.playlistcore.listener.ServiceCallbacks
import com.devbrackets.android.playlistcore.util.MediaProgressPoll

open class DefaultPlaylistHandler<I : PlaylistItem, out M : BasePlaylistManager<I>> protected constructor(
        protected val context: Context,
        protected val serviceClass: Class<out Service>,
        protected val playlistManager: M,
        protected val imageProvider: ImageProvider<I>,
        protected val notificationProvider: PlaylistNotificationProvider,
        protected val mediaSessionProvider: MediaSessionProvider,
        protected val mediaControlsHelper: MediaControlsHelper,
        protected val audioFocusHelper: AudioFocusHelper
) : PlaylistHandler<I>(), AudioFocusHelper.AudioFocusCallback, ProgressListener, MediaStatusListener<I> {

    companion object {
        const val TAG = "DefaultPlaylistHandler"
    }


    protected val mediaInfo = MediaInfo()
    protected val wifiLock = SafeWifiLock(context)

    protected var mediaProgressPoll = MediaProgressPoll<I>()

    protected var currentMediaPlayer: MediaPlayerApi<I>? = null

    protected val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    protected lateinit var serviceCallbacks: ServiceCallbacks

    /**
     * Retrieves the ID to use for the notification and registering this
     * service as Foreground when media is playing
     */
    protected open val notificationId: Int
        get() = R.id.playlistcore_default_notification_id

    /**
     * Determines if media is currently playing
     */
    protected val isPlaying: Boolean
        get() = currentMediaPlayer?.isPlaying ?: false

    protected val isLoading: Boolean
        get() {
            return currentPlaybackState == PlaybackState.RETRIEVING ||
                    currentPlaybackState == PlaybackState.PREPARING ||
                    currentPlaybackState == PlaybackState.SEEKING
        }

    var currentPlaylistItem: I? = null

    protected var pausedForSeek = false
    protected var playingBeforeSeek = false
    protected var pausedForFocusLoss = false

    protected var startPaused = false
    protected var seekToPosition: Long = -1

    override fun setup(serviceCallbacks: ServiceCallbacks) {
        this.serviceCallbacks = serviceCallbacks

        mediaProgressPoll.progressListener = this
        audioFocusHelper.setAudioFocusCallback(this)

        playlistManager.playlistHandler = this
    }

    override fun tearDown() {
        setPlaybackState(PlaybackState.STOPPED)

        relaxResources(true)
        playlistManager.playlistHandler = null
        audioFocusHelper.setAudioFocusCallback(null)

        mediaInfo.clear()
    }

    override fun play() {
        if (!isPlaying) {
            currentMediaPlayer?.play()
        }

        mediaProgressPoll.start()
        setPlaybackState(PlaybackState.PLAYING)
        setupForeground()

        requestAudioFocus()
        updateMediaControls()
    }

    override fun pause() {
        if (isPlaying) {
            currentMediaPlayer?.pause()
        }

        mediaProgressPoll.stop()
        setPlaybackState(PlaybackState.PAUSED)
        serviceCallbacks.endForeground(false)

        abandonAudioFocus()
        updateMediaControls()
    }

    override fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun stop() {
        setPlaybackState(PlaybackState.STOPPED)
        currentPlaylistItem?.let {
            playlistManager.serviceListener?.onMediaStopped(it)
        }

        // let go of all resources
        relaxResources(true)

        playlistManager.reset()
        serviceCallbacks.stop()
    }

    override fun next() {
        playlistManager.next()
        startItemPlayback(0, !isPlaying)
    }

    override fun previous() {
        playlistManager.previous()
        startItemPlayback(0, !isPlaying)
    }

    override fun startSeek() {
        if (isPlaying) {
            pausedForSeek = true
            pause()
        }
    }

    override fun seek(positionMillis: Long) {
        performSeek(positionMillis)
    }

    override fun onPrepared(mediaPlayer: MediaPlayerApi<I>) {
        startMediaPlayer()
    }

    override fun onBufferingUpdate(mediaPlayer: MediaPlayerApi<I>, percent: Int) {
        //Makes sure to update listeners of buffer updates even when playback is paused
        if (!mediaPlayer.isPlaying && currentMediaProgress.bufferPercent != percent) {
            currentMediaProgress.update(mediaPlayer.currentPosition, percent, mediaPlayer.duration)
            onProgressUpdated(currentMediaProgress)
        }
    }

    override fun onSeekComplete(mediaPlayer: MediaPlayerApi<I>) {
        if (pausedForSeek || playingBeforeSeek) {
            play()
            pausedForSeek = false
            playingBeforeSeek = false
        } else {
            pause()
        }
    }

    override fun onCompletion(mediaPlayer: MediaPlayerApi<I>) {
        // Handles moving to the next playable item
        next()
        startPaused = false
    }

    override fun onError(mediaPlayer: MediaPlayerApi<I>): Boolean {
        setPlaybackState(PlaybackState.ERROR)

        serviceCallbacks.endForeground(true)
        wifiLock.release()
        mediaProgressPoll.stop()

        abandonAudioFocus()
        return false
    }

    override fun onAudioFocusGained(): Boolean {
        if (currentMediaPlayer?.handlesOwnAudioFocus ?: false) {
            return false
        }

        //Returns the audio to the previous playback state and volume
        if (!isPlaying && pausedForFocusLoss) {
            pausedForFocusLoss = false
            play()
        } else {
            currentMediaPlayer?.setVolume(1.0f, 1.0f) //reset the audio volume
        }

        return true
    }

    // TODO: we need to account for media player changes during playback
    override fun onAudioFocusLost(canDuckAudio: Boolean): Boolean {
        if (currentMediaPlayer?.handlesOwnAudioFocus ?: false) {
            return false
        }

        //Either pauses or reduces the volume of the audio in playback
        if (!canDuckAudio) {
            if (isPlaying) {
                pausedForFocusLoss = true
                pause()
            }
        } else {
            currentMediaPlayer?.setVolume(0.1f, 0.1f)
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

    protected open fun setupForeground() {
        serviceCallbacks.runAsForeground(notificationId, notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), serviceClass))
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
    protected open fun performSeek(position: Long, updatePlaybackState: Boolean = true) {
        playingBeforeSeek = isPlaying
        currentMediaPlayer?.seekTo(position)

        if (updatePlaybackState) {
            setPlaybackState(PlaybackState.SEEKING)
        }
    }

    protected open fun initializeMediaPlayer(mediaPlayer: MediaPlayerApi<I>) {
        mediaPlayer.apply {
            stop()
            reset()
            setMediaStatusListener(this@DefaultPlaylistHandler)
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
        mediaInfo.notificationId = notificationId
        mediaInfo.playlistItem = currentPlaylistItem

        mediaInfo.appIcon = imageProvider.notificationIconRes
        mediaInfo.artwork = imageProvider.remoteViewArtwork
        mediaInfo.largeNotificationIcon = imageProvider.largeNotificationImage
    }

    override fun updateMediaControls() {
        //TODO: if the current item is null we should dismiss the notification (controls)
        if (currentPlaylistItem == null) {
            return
        }

        updateMediaInfo()
        mediaSessionProvider.update(mediaInfo)
        mediaControlsHelper.update(mediaInfo, mediaSessionProvider.get())

        // Updates the notification
        notificationManager.notify(mediaInfo.notificationId, notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), serviceClass))
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
        wifiLock.release()
        serviceCallbacks.endForeground(true)

        notificationManager.cancel(notificationId)
        mediaSessionProvider.get().release()
    }

    /**
     * Requests the audio focus
     */
    protected open fun requestAudioFocus(): Boolean {
        return currentMediaPlayer?.handlesOwnAudioFocus ?: false || audioFocusHelper.requestFocus()
    }

    /**
     * Requests the audio focus to be abandoned
     */
    protected open fun abandonAudioFocus(): Boolean {
        return currentMediaPlayer?.handlesOwnAudioFocus ?: false || audioFocusHelper.abandonFocus()
    }

    override fun startItemPlayback(positionMillis: Long, startPaused: Boolean) {
        this.seekToPosition = positionMillis
        this.startPaused = startPaused

        playlistManager.serviceListener?.onMediaPlaybackEnded()
        getNextPlayableItem()

        val item = currentPlaylistItem
        currentMediaPlayer = item?.let { getMediaPlayerForItem(it) }

        mediaItemChanged(item)
        if (play(currentMediaPlayer, item)) {
            return
        }

        //If the playback wasn't handled, attempt to seek to the next playable item, otherwise stop the service
        if (playlistManager.isNextAvailable) {
            next()
        } else {
            stop()
        }
    }

    protected open fun getMediaPlayerForItem(item: I): MediaPlayerApi<I>? {
        // We prioritize players higher in the list over the currentMediaPlayer
        return mediaPlayers.firstOrNull { it.handlesItem(item) }
    }

    /**
     * Starts the actual playback of the specified audio item.
     *
     * @return True if the item playback was correctly handled
     */
    protected open fun play(mediaPlayer: MediaPlayerApi<I>?, item: I?): Boolean {
        if (mediaPlayer == null || item == null) {
            return false
        }

        initializeMediaPlayer(mediaPlayer)
        requestAudioFocus()

        mediaPlayer.playItem(item)

        setPlaybackState(PlaybackState.PREPARING)
        setupForeground()

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        wifiLock.update(!(currentPlaylistItem?.downloaded ?: true))
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
        //TODO the audio focus functionality here can (and should be) handled by the normal path
        if (!(currentMediaPlayer?.handlesOwnAudioFocus ?: true)) {
            if (audioFocusHelper.currentAudioFocus == AudioFocusHelper.Focus.NO_FOCUS_NO_DUCK) {
                // If we don't have audio focus and can't duck we have to pause, even if state is playing
                // Be we stay in the playing state so we know we have to resume playback once we get the focus back.
                if (isPlaying) {
                    pausedForFocusLoss = true
                    pause()
                    playlistManager.serviceListener?.onMediaPlaybackEnded(currentPlaylistItem!!, currentMediaPlayer!!.currentPosition, currentMediaPlayer!!.duration)
                }

                return
            } else if (audioFocusHelper.currentAudioFocus == AudioFocusHelper.Focus.NO_FOCUS_CAN_DUCK) {
                currentMediaPlayer?.setVolume(0.1f, 0.1f)
            } else {
                currentMediaPlayer?.setVolume(1.0f, 1.0f)
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
            play()
            playlistManager.serviceListener?.onMediaPlaybackStarted(currentPlaylistItem!!, currentMediaPlayer!!.currentPosition, currentMediaPlayer!!.duration)
        } else {
            setPlaybackState(PlaybackState.PAUSED)
        }
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
        item ?: playlistManager.serviceListener?.onNoNonNetworkItemsAvailable()
        currentPlaylistItem = item

        return currentPlaylistItem
    }

    protected open fun isPlayable(item: I): Boolean {
        return getMediaPlayerForItem(item) != null
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

        currentItemChange = PlaylistItemChange(item, playlistManager.isPreviousAvailable, playlistManager.isNextAvailable).apply {
            playlistManager.onPlaylistItemChanged(currentItem, hasNext, hasPrevious)
        }
    }

    /**
     * Updates the current PlaybackState and informs any listening classes.
     *
     * @param state The new PlaybackState
     */
    protected open fun setPlaybackState(state: PlaybackState) {
        currentPlaybackState = state
        playlistManager.onPlaybackStateChanged(state)
    }

    open class Builder<I : PlaylistItem, out M : BasePlaylistManager<I>>(
            protected val context: Context,
            protected val serviceClass: Class<out Service>,
            protected val playlistManager: M,
            protected val imageProvider: ImageProvider<I>
    ) {
        var notificationProvider: PlaylistNotificationProvider? = null
        var mediaSessionProvider: MediaSessionProvider? = null
        var mediaControlsHelper: MediaControlsHelper? = null
        var audioFocusHelper: AudioFocusHelper? = null

        fun build(): DefaultPlaylistHandler<I, M> {
            return DefaultPlaylistHandler(context,
                    serviceClass,
                    playlistManager,
                    imageProvider,
                    notificationProvider ?: DefaultPlaylistNotificationProvider(context),
                    mediaSessionProvider ?: DefaultMediaSessionProvider(context, serviceClass),
                    mediaControlsHelper ?: MediaControlsHelper(context),
                    audioFocusHelper ?: AudioFocusHelper(context))
        }
    }
}