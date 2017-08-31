package com.devbrackets.android.playlistcoredemo.helper.cast;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.devbrackets.android.exomedia.util.MediaSourceUtil;
import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.listener.MediaStatusListener;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * A Simple implementation of the {@link MediaPlayerApi} that handles Chromecast
 */
public class CastMediaPlayer implements MediaPlayerApi<MediaItem> {
    private enum ConnectionStatus {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED
    }

    private static final String TAG = "CastMediaPlayer";
    @NonNull
    private static final Map<String, String> extensionToMimeMap = new HashMap<>();

    static {
        extensionToMimeMap.put(".mp3", MimeTypes.AUDIO_MPEG);
        extensionToMimeMap.put(".mp4", MimeTypes.VIDEO_MP4);
        extensionToMimeMap.put(".m3u8", MimeTypes.APPLICATION_M3U8);
        extensionToMimeMap.put(".mpd", "application/dash+xml");
        extensionToMimeMap.put(".ism", "application/vnd.ms-sstr+xml");
    }

    @NonNull
    private final SessionManagerListener<Session> sessionManagerListener = new CastSessionManagerListener();

    @Nullable
    private CastSession castSession;
    @Nullable
    private SessionManager sessionManager;
    @Nullable
    private MediaStatusListener<MediaItem> mediaStatusListener;

    @NonNull
    private ConnectionStatus connectionStatus = ConnectionStatus.NOT_CONNECTED;

    public CastMediaPlayer(@NonNull Context context) {
        sessionManager = CastContext.getSharedInstance(context).getSessionManager();

        castSession = sessionManager.getCurrentCastSession();
        sessionManager.addSessionManagerListener(sessionManagerListener);

        // Makes sure the connection state is accurate
        Session session = sessionManager.getCurrentSession();
        if (session != null) {
            if (session.isConnecting()) {
                connectionStatus = ConnectionStatus.CONNECTING;
            } else if (session.isConnected()) {
                connectionStatus = ConnectionStatus.CONNECTED;
            }
        }
    }

    @Override
    public boolean isPlaying() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            return remoteMediaClient.isPlaying();
        }

        return false;
    }

    @Override
    public boolean getHandlesOwnAudioFocus() {
        // Because the audio is playing on a separate device it "handles" the audio focus
        return true;
    }

    @Override
    public long getCurrentPosition() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            return remoteMediaClient.getApproximateStreamPosition();
        }

        return 0;
    }

    @Override
    public long getDuration() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            return remoteMediaClient.getStreamDuration();
        }

        return 0;
    }

    @Override
    public int getBufferedPercent() {
        return 0;
    }

    @Override
    public void play() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.play();
        }
    }

    @Override
    public void pause() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.pause();
        }
    }

    @Override
    public void stop() {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.stop();
        }
    }

    @Override
    public void reset() {
        //todo reset
    }

    @Override
    public void release() {
        if (sessionManager != null) {
            sessionManager.removeSessionManagerListener(sessionManagerListener);
        }

        castSession = null;
    }

    @Override
    public void setVolume(float left, float right) {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.setStreamVolume((left + right) / 2);
        }
    }

    @Override
    public void seekTo(long milliseconds) {
        castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.seek(milliseconds).setResultCallback(new ResultCallback<RemoteMediaClient.MediaChannelResult>() {
                @Override
                public void onResult(@NonNull RemoteMediaClient.MediaChannelResult mediaChannelResult) {
                    if (mediaStatusListener != null) {
                        mediaStatusListener.onSeekComplete(CastMediaPlayer.this);
                    }
                }
            });
        }
    }

    @Override
    public void setMediaStatusListener(@NonNull MediaStatusListener<MediaItem> listener) {
        this.mediaStatusListener = listener;
    }

    @Override
    public boolean handlesItem(@NonNull MediaItem item) {
        return connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING;
    }

    @Override
    public void playItem(@NonNull MediaItem item) {
        String mediaExtension = MediaSourceUtil.getExtension(Uri.parse(item.getMediaUrl()));
        String mimeType = getMimeFromExtension(mediaExtension);

        MediaMetadata mediaMetadata = new MediaMetadata(item.getMediaType() == BasePlaylistManager.VIDEO ? MediaMetadata.MEDIA_TYPE_MOVIE : MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);

        MediaInfo mediaInfo = new MediaInfo.Builder(item.getMediaUrl())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeType)
                .setMetadata(mediaMetadata)
                .build();

        if (castSession == null || castSession.isSuspended()) {
            castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;
        }

        if (castSession != null) {
            RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
            remoteMediaClient.load(mediaInfo, false, 0).setResultCallback(new ResultCallback<RemoteMediaClient.MediaChannelResult>() {
                @Override
                public void onResult(@NonNull RemoteMediaClient.MediaChannelResult mediaChannelResult) {
                    if (mediaStatusListener != null) {
                        mediaStatusListener.onPrepared(CastMediaPlayer.this);
                    }
                }
            });
        } else {
            if (mediaStatusListener != null) {
                mediaStatusListener.onError(this);
            }
        }
    }

    @Nullable
    private String getMimeFromExtension(@Nullable String extension) {
        if (extension == null || extension.trim().length() <= 0) {
            return null;
        }

        return extensionToMimeMap.get(extension);
    }


    /**
     * TODO:
     * 1. The listener is informed of the connection/disconnection
     * 2. When a session is resumed we need to make sure this media player is the active one
     * 3.
     */
    private class CastSessionManagerListener implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            connectionStatus = ConnectionStatus.CONNECTING;
            Log.d(TAG, "Cast session starting for session " + session.getSessionId());
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            connectionStatus = ConnectionStatus.CONNECTED;
            Log.d(TAG, "Cast session started for session " + session.getSessionId());
        }

        @Override
        public void onSessionStartFailed(Session session, int error) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            if (mediaStatusListener != null) {
                mediaStatusListener.onError(CastMediaPlayer.this); //todo hmmm, should we include an error with this?
            }

            Log.d(TAG, "Cast session failed to start for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(TAG, "Cast session ending for session " + session.getSessionId());
        }

        @Override
        public void onSessionEnded(Session session, int error) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            Log.d(TAG, "Cast session ended for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionResuming(Session session, String sessionId) {
            connectionStatus = ConnectionStatus.CONNECTING;
            Log.d(TAG, "Cast session resuming for session " + sessionId);
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            connectionStatus = ConnectionStatus.CONNECTED;
            Log.d(TAG, "Cast session resumed for session " + session.getSessionId() + "; wasSuspended=" + wasSuspended);
        }

        @Override
        public void onSessionResumeFailed(Session session, int error) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            if (mediaStatusListener != null) {
                mediaStatusListener.onPrepared(CastMediaPlayer.this);
            }

            Log.d(TAG, "Cast session failed to resume for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionSuspended(Session session, int reason) {
            connectionStatus = ConnectionStatus.NOT_CONNECTED;
            String causeText;

            switch (reason) {
                case GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST:
                    causeText = "Network Loss";
                    break;
                case GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED:
                    causeText = "Disconnected";
                    break;
                default:
                    causeText = "Unknown";
            }

            Log.d(TAG, "Cast session suspended due to " + causeText);
        }
    }
}
