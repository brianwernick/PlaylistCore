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
import com.google.android.gms.common.images.WebImage;

import java.util.HashMap;
import java.util.Map;

/**
 * A Simple implementation of the {@link MediaPlayerApi} that handles Chromecast
 * todo; reconnecting has issues...
 * todo: notification play/pause state is wrong with this player
 */
public class CastMediaPlayer implements MediaPlayerApi<MediaItem> {
    public interface OnConnectionChangeListener {
        void onCastMediaPlayerConnectionChange(@NonNull CastMediaPlayer player, @NonNull RemoteConnectionState state);
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
    @NonNull
    private final OnConnectionChangeListener stateListener;

    @Nullable
    private SessionManager sessionManager;
    @Nullable
    private MediaStatusListener<MediaItem> mediaStatusListener;

    @NonNull
    private RemoteConnectionState remoteConnectionState = RemoteConnectionState.NOT_CONNECTED;

    public CastMediaPlayer(@NonNull Context context, @NonNull OnConnectionChangeListener listener) {
        stateListener = listener;

        sessionManager = CastContext.getSharedInstance(context).getSessionManager();
        sessionManager.addSessionManagerListener(sessionManagerListener);

        // Makes sure the connection state is accurate
        Session session = sessionManager.getCurrentSession();
        if (session != null) {
            if (session.isConnecting()) {
                updateState(RemoteConnectionState.CONNECTING);
            } else if (session.isConnected()) {
                updateState(RemoteConnectionState.CONNECTED);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        return remoteMediaClient != null && remoteMediaClient.isPlaying();
    }

    @Override
    public boolean getHandlesOwnAudioFocus() {
        // Because the audio is playing on a separate device it "handles" the audio focus
        return true;
    }

    @Override
    public long getCurrentPosition() {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
            return remoteMediaClient.getApproximateStreamPosition();
        }

        return 0;
    }

    @Override
    public long getDuration() {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
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
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.play();
        }
    }

    @Override
    public void pause() {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.pause();
        }
    }

    @Override
    public void stop() {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.stop();
        }
    }

    @Override
    public void reset() {
        // Purposefully left blank
    }

    @Override
    public void release() {
        if (sessionManager != null) {
            sessionManager.removeSessionManagerListener(sessionManagerListener);
        }
    }

    @Override
    public void setVolume(float left, float right) {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.setStreamVolume((left + right) / 2);
        }
    }

    @Override
    public void seekTo(long milliseconds) {
        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
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
        return remoteConnectionState == RemoteConnectionState.CONNECTED;// || remoteConnectionState == RemoteConnectionState.CONNECTING;
    }

    @Override
    public void playItem(@NonNull MediaItem item) {
        String mediaExtension = MediaSourceUtil.getExtension(Uri.parse(item.getMediaUrl()));
        String mimeType = getMimeFromExtension(mediaExtension);

        MediaMetadata mediaMetadata = new MediaMetadata(item.getMediaType() == BasePlaylistManager.VIDEO ? MediaMetadata.MEDIA_TYPE_MOVIE : MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, item.getTitle());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE, item.getAlbum());
        mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, item.getArtist());
        mediaMetadata.putString(MediaMetadata.KEY_ARTIST, item.getArtist());
        mediaMetadata.addImage(new WebImage(Uri.parse(item.getArtworkUrl())));

        MediaInfo mediaInfo = new MediaInfo.Builder(item.getMediaUrl())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mimeType)
                .setMetadata(mediaMetadata)
                .build();


        RemoteMediaClient remoteMediaClient = getMediaClient();
        if (remoteMediaClient != null) {
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

    @Nullable
    private RemoteMediaClient getMediaClient() {
        CastSession castSession = sessionManager != null ? sessionManager.getCurrentCastSession() : null;
        if (castSession != null) {
            return castSession.getRemoteMediaClient();
        }

        return null;
    }

    private void updateState(@NonNull RemoteConnectionState state) {
        remoteConnectionState = state;
        stateListener.onCastMediaPlayerConnectionChange(this, state);
    }

    private class CastSessionManagerListener implements SessionManagerListener<Session> {
        @Override
        public void onSessionStarting(Session session) {
            updateState(RemoteConnectionState.CONNECTING);
            Log.d(TAG, "Cast session starting for session " + session.getSessionId());
        }

        @Override
        public void onSessionStarted(Session session, String sessionId) {
            updateState(RemoteConnectionState.CONNECTED);
            Log.d(TAG, "Cast session started for session " + session.getSessionId());
        }

        @Override
        public void onSessionStartFailed(Session session, int error) {
            updateState(RemoteConnectionState.NOT_CONNECTED);
            if (mediaStatusListener != null) {
                mediaStatusListener.onError(CastMediaPlayer.this);
            }

            Log.d(TAG, "Cast session failed to start for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionEnding(Session session) {
            Log.d(TAG, "Cast session ending for session " + session.getSessionId());
        }

        @Override
        public void onSessionEnded(Session session, int error) {
            updateState(RemoteConnectionState.NOT_CONNECTED);
            Log.d(TAG, "Cast session ended for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionResuming(Session session, String sessionId) {
            updateState(RemoteConnectionState.CONNECTING);
            Log.d(TAG, "Cast session resuming for session " + sessionId);
        }

        @Override
        public void onSessionResumed(Session session, boolean wasSuspended) {
            updateState(RemoteConnectionState.CONNECTED);
            Log.d(TAG, "Cast session resumed for session " + session.getSessionId() + "; wasSuspended=" + wasSuspended);
        }

        @Override
        public void onSessionResumeFailed(Session session, int error) {
            updateState(RemoteConnectionState.NOT_CONNECTED);
            if (mediaStatusListener != null) {
                mediaStatusListener.onPrepared(CastMediaPlayer.this);
            }

            Log.d(TAG, "Cast session failed to resume for session " + session.getSessionId() + " with the error " + error);
        }

        @Override
        public void onSessionSuspended(Session session, int reason) {
            updateState(RemoteConnectionState.NOT_CONNECTED);
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
