package com.devbrackets.android.playlistcoredemo.helper.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devbrackets.android.exomedia.util.MediaSourceUtil
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.MediaPlayerApi.RemoteConnectionState
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.images.WebImage

/**
 * A Simple implementation of the [MediaPlayerApi] that handles Chromecast
 */
class CastMediaPlayer(
  context: Context,
  private val stateListener: OnConnectionChangeListener,
  private val infoChangeListener: OnMediaInfoChangeListener
) : MediaPlayerApi<MediaItem> {
  interface OnConnectionChangeListener {
    fun onCastMediaPlayerConnectionChange(player: CastMediaPlayer, state: RemoteConnectionState)
  }

  interface OnMediaInfoChangeListener {
    fun onMediaInfoChange(player: CastMediaPlayer)
  }

  private val sessionManagerListener: SessionManagerListener<Session> = CastSessionManagerListener()
  private val sessionManager: SessionManager?
  private var mediaStatusListener: MediaStatusListener<MediaItem>? = null
  private var remoteConnectionState = RemoteConnectionState.NOT_CONNECTED
  private val castResultCallback = CastResultCallback()
  private val seekResultCallback = SeekResultCallback()
  private val preparedResultCallback = PreparedResultCallback()

  init {
    sessionManager = CastContext.getSharedInstance(context).sessionManager
    sessionManager.addSessionManagerListener(sessionManagerListener)

    // Makes sure the connection state is accurate
    val session = sessionManager.currentSession
    if (session != null) {
      if (session.isConnecting) {
        updateState(RemoteConnectionState.CONNECTING)
      } else if (session.isConnected) {
        updateState(RemoteConnectionState.CONNECTED)
      }
    }
  }

  override val isPlaying: Boolean
    get() {
      val remoteMediaClient = mediaClient
      return remoteMediaClient != null && remoteMediaClient.isPlaying
    }

  // Because the audio is playing on a separate device it "handles" the audio focus
  override val handlesOwnAudioFocus: Boolean
    get() =// Because the audio is playing on a separate device it "handles" the audio focus
      true
  override val currentPosition: Long
    get() {
      val remoteMediaClient = mediaClient
      return remoteMediaClient?.approximateStreamPosition ?: 0
    }
  override val duration: Long
    get() {
      val remoteMediaClient = mediaClient
      return remoteMediaClient?.streamDuration ?: 0
    }
  override val bufferedPercent: Int
    get() = 0

  override fun play() {
    val remoteMediaClient = mediaClient
    remoteMediaClient?.play()?.setResultCallback(castResultCallback)
  }

  override fun pause() {
    val remoteMediaClient = mediaClient
    remoteMediaClient?.pause()?.setResultCallback(castResultCallback)
  }

  override fun stop() {
    val remoteMediaClient = mediaClient
    remoteMediaClient?.stop()?.setResultCallback(castResultCallback)
  }

  override fun reset() {
    // Purposefully left blank
  }

  override fun release() {
    sessionManager?.removeSessionManagerListener(sessionManagerListener)
  }

  override fun setVolume(left: Float, right: Float) {
    val remoteMediaClient = mediaClient
    remoteMediaClient?.setStreamVolume(((left + right) / 2).toDouble())
  }

  override fun seekTo(milliseconds: Long) {
    val remoteMediaClient = mediaClient
    remoteMediaClient?.seek(milliseconds)?.setResultCallback(seekResultCallback)
  }

  override fun setMediaStatusListener(listener: MediaStatusListener<MediaItem>) {
    mediaStatusListener = listener
  }

  override fun handlesItem(item: MediaItem): Boolean {
    return remoteConnectionState === RemoteConnectionState.CONNECTED
  }

  override fun playItem(item: MediaItem) {
    mediaClient?.let {
      val loadOptions = MediaLoadOptions.Builder()
        .setAutoplay(false)
        .setPlayPosition(0)
        .build()

      val mediaInfo = getMediaInfo(item)
      it.load(mediaInfo, loadOptions).setResultCallback(preparedResultCallback)
    } ?: mediaStatusListener?.onError(this)
  }

  private fun getMediaInfo(item: MediaItem): MediaInfo {
    val mediaExtension = MediaSourceUtil.getExtension(Uri.parse(item.mediaUrl))
    val mimeType = getMimeFromExtension(mediaExtension)
    val mediaType = when (item.mediaType) {
      BasePlaylistManager.VIDEO -> MediaMetadata.MEDIA_TYPE_MOVIE
      else -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
    }

    val mediaMetadata = MediaMetadata(mediaType).apply {
      putString(MediaMetadata.KEY_TITLE, item.title)
      putString(MediaMetadata.KEY_ALBUM_TITLE, item.album)
      putString(MediaMetadata.KEY_ALBUM_ARTIST, item.artist)
      putString(MediaMetadata.KEY_ARTIST, item.artist)
      addImage(WebImage(Uri.parse(item.artworkUrl)))
    }

    return MediaInfo.Builder(item.mediaUrl)
      .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
      .setContentType(mimeType)
      .setMetadata(mediaMetadata)
      .build()
  }


  private fun getMimeFromExtension(extension: String?): String? {
    return if (extension == null || extension.trim { it <= ' ' }.isEmpty()) {
      null
    } else extensionToMimeMap[extension]
  }

  private val mediaClient: RemoteMediaClient?
    get() {
      return sessionManager?.currentCastSession?.remoteMediaClient
    }

  private fun updateState(state: RemoteConnectionState) {
    remoteConnectionState = state
    stateListener.onCastMediaPlayerConnectionChange(this, state)
  }

  /**
   * Watches the result of a request for media change which will allow us to keep the
   * media state stored in the PlaylistHandler and the actual state this class represents
   * in sync.
   */
  private inner class CastResultCallback : ResultCallback<RemoteMediaClient.MediaChannelResult> {
    override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult) {
      infoChangeListener.onMediaInfoChange(this@CastMediaPlayer)
    }
  }

  /**
   * Handles listening to the initial load process to inform the listener the media was prepared
   */
  private inner class PreparedResultCallback : ResultCallback<RemoteMediaClient.MediaChannelResult> {
    override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult) {
      if (mediaStatusListener != null) {
        mediaStatusListener!!.onPrepared(this@CastMediaPlayer)
      }
    }
  }

  /**
   * Handles listening to the seek process to inform the listener we have finished
   */
  private inner class SeekResultCallback : ResultCallback<RemoteMediaClient.MediaChannelResult> {
    override fun onResult(mediaChannelResult: RemoteMediaClient.MediaChannelResult) {
      mediaStatusListener?.onSeekComplete(this@CastMediaPlayer)
    }
  }

  private inner class CastSessionManagerListener : SessionManagerListener<Session> {
    override fun onSessionStarting(session: Session) {
      updateState(RemoteConnectionState.CONNECTING)
      Log.d(TAG, "Cast session starting for session " + session.sessionId)
    }

    override fun onSessionStarted(session: Session, sessionId: String) {
      updateState(RemoteConnectionState.CONNECTED)
      Log.d(TAG, "Cast session started for session " + session.sessionId)
    }

    override fun onSessionStartFailed(session: Session, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      mediaStatusListener?.onError(this@CastMediaPlayer)
      Log.d(TAG, "Cast session failed to start for session " + session.sessionId + " with the error " + error)
    }

    override fun onSessionEnding(session: Session) {
      Log.d(TAG, "Cast session ending for session " + session.sessionId)
    }

    override fun onSessionEnded(session: Session, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      Log.d(TAG, "Cast session ended for session " + session.sessionId + " with the error " + error)
    }

    override fun onSessionResuming(session: Session, sessionId: String) {
      updateState(RemoteConnectionState.CONNECTING)
      Log.d(TAG, "Cast session resuming for session $sessionId")
    }

    override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
      updateState(RemoteConnectionState.CONNECTED)
      Log.d(TAG, "Cast session resumed for session " + session.sessionId + "; wasSuspended=" + wasSuspended)
    }

    override fun onSessionResumeFailed(session: Session, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      mediaStatusListener?.onPrepared(this@CastMediaPlayer)
      Log.d(TAG, "Cast session failed to resume for session " + session.sessionId + " with the error " + error)
    }

    override fun onSessionSuspended(session: Session, reason: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      val causeText: String
      causeText = when (reason) {
        GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST -> "Network Loss"
        GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED -> "Disconnected"
        else -> "Unknown"
      }
      Log.d(TAG, "Cast session suspended due to $causeText")
    }
  }

  companion object {
    private const val TAG = "CastMediaPlayer"
    private val extensionToMimeMap: MutableMap<String, String> = HashMap()

    init {
      extensionToMimeMap[".mp3"] = MimeTypes.AUDIO_MPEG
      extensionToMimeMap[".mp4"] = MimeTypes.VIDEO_MP4
      extensionToMimeMap[".m3u8"] = MimeTypes.APPLICATION_M3U8
      extensionToMimeMap[".mpd"] = "application/dash+xml"
      extensionToMimeMap[".ism"] = "application/vnd.ms-sstr+xml"
    }
  }
}