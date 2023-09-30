package com.devbrackets.android.playlistcoredemo.helper.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.MediaPlayerApi.RemoteConnectionState
import com.devbrackets.android.playlistcore.listener.MediaStatusListener
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcoredemo.data.MediaItem
import com.devbrackets.android.playlistcoredemo.helper.getContentType
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import java.util.concurrent.Executors

/**
 * A Simple implementation of the [MediaPlayerApi] that handles Chromecast.
 *
 * NOTE:
 * Attempting to connect to a Chromecast when the mobile device is connected to a
 * VPN will result in a failed connection because the mobile device will appear to
 * be on a separate network.
 */
class CastMediaPlayer(
  context: Context,
  private val stateListener: OnConnectionChangeListener,
  private val infoChangeListener: OnMediaInfoChangeListener
) : MediaPlayerApi<MediaItem> {
  companion object {
    private const val TAG = "CastMediaPlayer"
  }

  interface OnConnectionChangeListener {
    fun onCastMediaPlayerConnectionChange(player: CastMediaPlayer, state: RemoteConnectionState)
  }

  interface OnMediaInfoChangeListener {
    fun onMediaInfoChange(player: CastMediaPlayer)
  }

  private var sessionManager: SessionManager? = null
  private val sessionManagerListener = CastSessionManagerListener()

  private var mediaStatusListener: MediaStatusListener<MediaItem>? = null
  private var remoteConnectionState = RemoteConnectionState.NOT_CONNECTED

  private val mediaClient: RemoteMediaClient?
    get() = sessionManager?.currentCastSession?.remoteMediaClient

  override val isPlaying: Boolean
    get() = mediaClient?.isPlaying == true

  // Because the audio is playing on a separate device it "handles" the audio focus
  override val handlesOwnAudioFocus: Boolean
    get() = true

  override val currentPosition: Long
    get() = mediaClient?.approximateStreamPosition ?: 0

  override val duration: Long
    get() = mediaClient?.streamDuration ?: 0

  override val bufferedPercent: Int
    get() = 0

  init {
    CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor()).addOnSuccessListener {
      initializeWithSessionManager(it.sessionManager)
    }
  }

  override fun play() {
    mediaClient?.play()?.setResultCallback {
      infoChangeListener.onMediaInfoChange(this)
    }
  }

  override fun pause() {
    mediaClient?.pause()?.setResultCallback {
      infoChangeListener.onMediaInfoChange(this)
    }
  }

  override fun stop() {
    mediaClient?.stop()?.setResultCallback {
      infoChangeListener.onMediaInfoChange(this)
    }
  }

  override fun reset() {
    // Purposefully left blank
  }

  override fun release() {
    sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
  }

  override fun setVolume(left: Float, right: Float) {
    mediaClient?.setStreamVolume(((left + right) / 2).toDouble())
  }

  override fun seekTo(milliseconds: Long) {
    val options = MediaSeekOptions.Builder().apply {
      setPosition(milliseconds)
    }.build()

    mediaClient?.seek(options)?.setResultCallback {
      mediaStatusListener?.onSeekComplete(this)
    }
  }

  override fun setMediaStatusListener(listener: MediaStatusListener<MediaItem>) {
    mediaStatusListener = listener
  }

  override fun handlesItem(item: MediaItem): Boolean {
    return remoteConnectionState == RemoteConnectionState.CONNECTED ||
        remoteConnectionState == RemoteConnectionState.CONNECTING
  }

  override fun playItem(item: MediaItem) {
    val client = mediaClient
    if (client == null) {
      mediaStatusListener?.onError(this)
      return
    }

    val requestData = MediaLoadRequestData.Builder().apply {
      setMediaInfo(getMediaInfo(item))
      setAutoplay(false)
      setCurrentTime(0)
    }.build()

    client.load(requestData).setResultCallback {
      mediaStatusListener?.onPrepared(this)
    }
  }

  private fun initializeWithSessionManager(manager: SessionManager) {
    sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
    sessionManager = manager

    manager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

    // Makes sure the connection state is accurate
    manager.currentSession?.let { session ->
      if (session.isConnecting) {
        updateState(RemoteConnectionState.CONNECTING)
      } else if (session.isConnected) {
        updateState(RemoteConnectionState.CONNECTED)
      }
    }
  }

  private fun getMediaInfo(item: MediaItem): MediaInfo {
    val contentType = Uri.parse(item.mediaUrl).getContentType()

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

    return MediaInfo.Builder(item.mediaUrl).apply {
      setContentUrl(item.mediaUrl)
      setContentType(contentType)
      setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
      setMetadata(mediaMetadata)
    }.build()
  }

  private fun updateState(state: RemoteConnectionState) {
    remoteConnectionState = state
    stateListener.onCastMediaPlayerConnectionChange(this, state)
  }

  private inner class CastSessionManagerListener : SessionManagerListener<CastSession> {
    override fun onSessionStarting(session: CastSession) {
      updateState(RemoteConnectionState.CONNECTING)
      logSession("Cast session starting", session)
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
      updateState(RemoteConnectionState.CONNECTED)
      logSession("Cast session started", session)
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      mediaStatusListener?.onError(this@CastMediaPlayer)

      val errorString = "\"${CastStatusCodes.getStatusCodeString(error)}\" ($error)"
      logSession("Cast session failed to start with error $errorString", session)
    }

    override fun onSessionEnding(session: CastSession) {
      logSession("Cast session ending", session)
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)

      val errorString = "\"${CastStatusCodes.getStatusCodeString(error)}\" ($error)"
      logSession("Cast session ended with error $errorString", session)
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
      updateState(RemoteConnectionState.CONNECTING)
      logSession("Cast session resuming", session)
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
      updateState(RemoteConnectionState.CONNECTED)
      logSession("Cast session resumed; wasSuspended=$wasSuspended", session)
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      mediaStatusListener?.onPrepared(this@CastMediaPlayer)

      val errorString = "\"${CastStatusCodes.getStatusCodeString(error)}\" ($error)"
      logSession("Cast session failed to resume with error $errorString", session)
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
      updateState(RemoteConnectionState.NOT_CONNECTED)
      val causeText: String = when (reason) {
        CastStatusCodes.NETWORK_ERROR -> "Network Loss"
        CastStatusCodes.ERROR_SERVICE_DISCONNECTED -> "Disconnected"
        else -> "Unknown"
      }

      logSession("Cast session suspended due to $causeText", session)
    }

    private fun logSession(message: String, session: CastSession) {
      val status = when {
        session.isConnecting -> "connecting"
        session.isConnected -> "connected"
        session.isDisconnecting -> "disconnecting"
        session.isDisconnected -> "disconnected"
        session.isResuming -> "resuming"
        session.isSuspended -> "suspended"
        else -> "unknown"
      }

      val fields = listOf(
        "sessionId" to session.sessionId,
        "category" to session.category,
        "status" to status
      )

      val sessionText = fields.joinToString(
        separator = ", ",
        prefix = "[",
        postfix = "]",
        transform = {
          "${it.first}=${it.second.toString()}"
        }
      )

      Log.d(TAG, "$message (session: $sessionText)")
    }
  }
}