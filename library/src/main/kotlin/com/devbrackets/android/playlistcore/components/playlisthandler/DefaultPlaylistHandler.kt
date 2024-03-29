package com.devbrackets.android.playlistcore.components.playlisthandler

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.audiofocus.AudioFocusProvider
import com.devbrackets.android.playlistcore.components.audiofocus.DefaultAudioFocusProvider
import com.devbrackets.android.playlistcore.components.image.ImageProvider
import com.devbrackets.android.playlistcore.components.mediacontrols.DefaultMediaControlsProvider
import com.devbrackets.android.playlistcore.components.mediacontrols.MediaControlsProvider
import com.devbrackets.android.playlistcore.components.mediasession.DefaultMediaSessionProvider
import com.devbrackets.android.playlistcore.components.mediasession.MediaSessionProvider
import com.devbrackets.android.playlistcore.components.notification.DefaultPlaylistNotificationProvider
import com.devbrackets.android.playlistcore.components.notification.PlaylistNotificationProvider
import com.devbrackets.android.playlistcore.data.MediaInfo
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.data.PlaylistItemChange
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcore.listener.ProgressListener
import com.devbrackets.android.playlistcore.listener.ServiceCallbacks
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.util.MediaProgressPoll
import com.devbrackets.android.playlistcore.util.SafeWifiLock

@Suppress("MemberVisibilityCanBePrivate")
open class DefaultPlaylistHandler<I : PlaylistItem, out M : BasePlaylistManager<I>> protected constructor(
  protected val context: Context,
  protected val serviceClass: Class<out Service>,
  protected val playlistManager: M,
  protected val imageProvider: ImageProvider<I>,
  protected val notificationProvider: PlaylistNotificationProvider,
  protected val mediaSessionProvider: MediaSessionProvider,
  protected val mediaControlsProvider: MediaControlsProvider,
  protected val audioFocusProvider: AudioFocusProvider<I>,
  var listener: Listener<I>?
) : PlaylistHandler<I>(playlistManager.mediaPlayers), ProgressListener, MediaStatusListener<I> {

  companion object {
    const val TAG = "DefaultPlaylistHandler"
  }

  interface Listener<I : PlaylistItem> {
    fun onMediaPlayerChanged(oldPlayer: MediaPlayerApi<I>?, newPlayer: MediaPlayerApi<I>?)
    fun onItemSkipped(item: I)
  }

  protected val mediaInfo = MediaInfo()
  protected val wifiLock = SafeWifiLock(context)

  protected var mediaProgressPoll = MediaProgressPoll<I>()

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
  protected open val isPlaying: Boolean
    get() = currentMediaPlayer?.isPlaying ?: false

  protected open val isLoading: Boolean
    get() {
      return currentPlaybackState == PlaybackState.RETRIEVING ||
          currentPlaybackState == PlaybackState.PREPARING ||
          currentPlaybackState == PlaybackState.SEEKING
    }

  var currentPlaylistItem: I? = null

  protected var pausedForSeek = false
  protected var playingBeforeSeek = false

  protected var startPaused = false
  protected var seekToPosition: Long = -1

  protected var sequentialErrors: Int = 0

  init {
    audioFocusProvider.setPlaylistHandler(this)
  }

  override fun setup(serviceCallbacks: ServiceCallbacks) {
    this.serviceCallbacks = serviceCallbacks

    mediaProgressPoll.progressListener = this
    playlistManager.playlistHandler = this
  }

  override fun tearDown() {
    setPlaybackState(PlaybackState.STOPPED)

    relaxResources()
    playlistManager.playlistHandler = null

    mediaInfo.clear()
  }

  override fun play() {
    if (!isPlaying) {
      currentMediaPlayer?.play()
    }

    mediaProgressPoll.start()
    setPlaybackState(PlaybackState.PLAYING)

    setupForeground()
    audioFocusProvider.requestFocus()
  }

  override fun pause(transient: Boolean) {
    if (isPlaying) {
      currentMediaPlayer?.pause()
    }

    mediaProgressPoll.stop()
    setPlaybackState(PlaybackState.PAUSED)
    serviceCallbacks.endForeground(false)

    if (!transient) {
      audioFocusProvider.abandonFocus()
    }
  }

  override fun togglePlayPause() {
    if (isPlaying) {
      pause(false)
    } else {
      play()
    }
  }

  override fun stop() {
    currentMediaPlayer?.stop()

    setPlaybackState(PlaybackState.STOPPED)
    currentPlaylistItem?.let {
      playlistManager.playbackStatusListener?.onItemPlaybackEnded(it)
    }

    // let go of all resources
    relaxResources()

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
      pause(true)
    }
  }

  override fun seek(positionMillis: Long) {
    performSeek(positionMillis)
  }

  override fun onPrepared(mediaPlayer: MediaPlayerApi<I>) {
    startMediaPlayer(mediaPlayer)
    sequentialErrors = 0
  }

  override fun onBufferingUpdate(mediaPlayer: MediaPlayerApi<I>, percent: Int) {
    // Makes sure to update listeners of buffer updates even when playback is paused
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
      pause(false)
    }
  }

  override fun onCompletion(mediaPlayer: MediaPlayerApi<I>) {
    // Handles moving to the next playable item
    next()
    startPaused = false
  }

  override fun onError(mediaPlayer: MediaPlayerApi<I>): Boolean {
    // Unless we've had 3 or more errors without an item successfully playing we will move to the next item
    if (++sequentialErrors <= 3) {
      next()
      return false
    }

    setPlaybackState(PlaybackState.ERROR)

    serviceCallbacks.endForeground(true)
    wifiLock.release()
    mediaProgressPoll.stop()

    audioFocusProvider.abandonFocus()
    return false
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
    val notification = notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), serviceClass)
    serviceCallbacks.runAsForeground(notificationId, notification)
  }

  /**
   * Performs the functionality to seek the current media item
   * to the specified position.  This should only be called directly
   * when performing the initial setup of playback position. For
   * normal seeking process use the [performSeekStarted] in
   * conjunction with [performSeekEnded]
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

    mediaInfo.playbackPositionMs = currentMediaPlayer?.currentPosition ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
    mediaInfo.playbackDurationMs = currentMediaPlayer?.duration ?: -1
  }

  override fun updateMediaControls() {
    if (currentPlaylistItem == null) {
      return
    }

    updateMediaInfo()
    mediaSessionProvider.update(mediaInfo)
    mediaControlsProvider.update(mediaInfo, mediaSessionProvider.get())

    // Updates the notification
    val notification = notificationProvider.buildNotification(mediaInfo, mediaSessionProvider.get(), serviceClass)
    notificationManager.notify(mediaInfo.notificationId, notification)
  }

  override fun refreshCurrentMediaPlayer() {
    refreshCurrentMediaPlayer(currentMediaPlayer?.currentPosition ?: seekToPosition, !isPlaying)
  }

  protected open fun refreshCurrentMediaPlayer(seekPosition: Long, startPaused: Boolean) {
    currentPlaylistItem.let {
      seekToPosition = seekPosition
      this.startPaused = startPaused

      updateCurrentMediaPlayer(it)
      if (!play(currentMediaPlayer, it)) {
        next()
      }
    }
  }

  override fun onRemoteMediaPlayerConnectionChange(mediaPlayer: MediaPlayerApi<I>, state: MediaPlayerApi.RemoteConnectionState) {
    // If the mediaPlayer that changed state is of lower priority than the current one we ignore the change
    currentMediaPlayer?.let {
      if (mediaPlayers.indexOf(it) < mediaPlayers.indexOf(mediaPlayer)) {
        Log.d(TAG, "Ignoring remote connection state change for $mediaPlayer because it is of lower priority than the current MediaPlayer")
        return
      }
    }

    when (state) {
      MediaPlayerApi.RemoteConnectionState.CONNECTING -> {
        if (mediaPlayer != currentMediaPlayer) {
          val resumePlayback = isPlaying
          pause(true)
          seekToPosition = currentMediaPlayer?.currentPosition ?: seekToPosition
          startPaused = !resumePlayback
        }
        return
      }
      MediaPlayerApi.RemoteConnectionState.CONNECTED -> {
        if (mediaPlayer != currentMediaPlayer) {
          refreshCurrentMediaPlayer(currentMediaProgress.position, startPaused)
        }
      }
      MediaPlayerApi.RemoteConnectionState.NOT_CONNECTED -> {
        if (mediaPlayer == currentMediaPlayer) {
          refreshCurrentMediaPlayer(currentMediaProgress.position, startPaused)
        }
      }
    }
  }

  /**
   * Releases resources used by the service for playback. This includes the "foreground service"
   * status and notification, the wake locks, and the audioPlayer if requested
   */
  protected open fun relaxResources() {
    mediaProgressPoll.release()
    currentMediaPlayer = null

    audioFocusProvider.abandonFocus()
    wifiLock.release()
    serviceCallbacks.endForeground(true)

    notificationManager.cancel(notificationId)
    mediaSessionProvider.get().release()
  }

  override fun startItemPlayback(positionMillis: Long, startPaused: Boolean) {
    this.seekToPosition = positionMillis
    this.startPaused = startPaused

    playlistManager.playbackStatusListener?.onItemPlaybackEnded(currentPlaylistItem)
    currentPlaylistItem = getNextPlayableItem()

    currentPlaylistItem.let {
      updateCurrentMediaPlayer(it)
      mediaItemChanged(it)

      if (play(currentMediaPlayer, it)) {
        return
      }
    }

    // If the playback wasn't handled, attempt to seek to the next playable item, otherwise stop the service
    if (playlistManager.isNextAvailable) {
      next()
    } else {
      stop()
    }
  }

  protected open fun updateCurrentMediaPlayer(item: I?) {
    if (mediaPlayers.isEmpty()) {
      Log.d(TAG, "No media players available, stopping service")
      stop()
    }

    val newMediaPlayer = item?.let { getMediaPlayerForItem(it) }
    if (newMediaPlayer != currentMediaPlayer) {
      listener?.onMediaPlayerChanged(currentMediaPlayer, newMediaPlayer)
      currentMediaPlayer?.stop()
    }

    currentMediaPlayer = newMediaPlayer
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
    audioFocusProvider.requestFocus()

    mediaPlayer.playItem(item)

    setupForeground()
    setPlaybackState(PlaybackState.PREPARING)

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
  protected open fun startMediaPlayer(mediaPlayer: MediaPlayerApi<I>) {
    // Seek to the correct position
    val seekRequested = seekToPosition > 0
    if (seekRequested) {
      performSeek(seekToPosition, false)
      seekToPosition = -1
    }

    // Start the playback only if requested, otherwise update the state to paused
    mediaProgressPoll.start()
    if (!mediaPlayer.isPlaying && !startPaused) {
      pausedForSeek = seekRequested
      play()
      playlistManager.playbackStatusListener?.onMediaPlaybackStarted(currentPlaylistItem!!, mediaPlayer.currentPosition, mediaPlayer.duration)
    } else {
      setPlaybackState(PlaybackState.PAUSED)
    }

    audioFocusProvider.refreshFocus()
  }

  /**
   * Iterates through the playList, starting with the current item, until we reach an item we can play.
   * Normally this will be the current item, however if they don't have network then
   * it will be the next downloaded item.
   */
  protected open fun getNextPlayableItem(): I? {
    var item = playlistManager.currentItem
    while (item != null && getMediaPlayerForItem(item) == null) {
      listener?.onItemSkipped(item)
      item = playlistManager.next()
    }

    //If we are unable to get a next playable item, inform the listener we are at the end of the playlist
    item ?: playlistManager.playbackStatusListener?.onPlaylistEnded()
    return item
  }

  /**
   * Called when the current media item has changed, this will update the notification and
   * media control values.
   */
  protected open fun mediaItemChanged(item: I?) {
    // Validates that the currentPlaylistItem is for the currentItem
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

    // Makes sure the Media Controls are up-to-date
    if (state != PlaybackState.STOPPED && state != PlaybackState.ERROR) {
      updateMediaControls()
    }
  }

  open class Builder<I : PlaylistItem, out M : BasePlaylistManager<I>>(
    protected val context: Context,
    protected val serviceClass: Class<out Service>,
    protected val playlistManager: M,
    protected val imageProvider: ImageProvider<I>
  ) {
    var notificationProvider: PlaylistNotificationProvider? = null
    var mediaSessionProvider: MediaSessionProvider? = null
    var mediaControlsProvider: MediaControlsProvider? = null
    var audioFocusProvider: AudioFocusProvider<I>? = null
    var listener: Listener<I>? = null

    fun build(): DefaultPlaylistHandler<I, M> {
      return DefaultPlaylistHandler(
        context,
        serviceClass,
        playlistManager,
        imageProvider,
        notificationProvider ?: DefaultPlaylistNotificationProvider(context),
        mediaSessionProvider ?: DefaultMediaSessionProvider(context, serviceClass),
        mediaControlsProvider ?: DefaultMediaControlsProvider(context),
        audioFocusProvider ?: DefaultAudioFocusProvider(context),
        listener
      )
    }
  }
}