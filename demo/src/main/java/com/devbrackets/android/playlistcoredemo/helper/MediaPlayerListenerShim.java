package com.devbrackets.android.playlistcoredemo.helper;

import android.media.MediaPlayer;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.listener.OnMediaCompletionListener;
import com.devbrackets.android.playlistcore.listener.OnMediaErrorListener;
import com.devbrackets.android.playlistcore.listener.OnMediaPreparedListener;
import com.devbrackets.android.playlistcore.listener.OnMediaSeekCompletionListener;

/**
 * A simple class that handles joining of the MediaPlayer listeners
 * to the PlaylistCore media listeners.  This could also be built in to the
 * {@link AudioApi} and {@link VideoApi} themselves, but to to keep those
 * clean for the demo, we have this shim class
 */
public class MediaPlayerListenerShim implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener  {
    @Nullable
    private Pair<MediaPlayerApi, OnMediaPreparedListener> onMediaPreparedListener;
    @Nullable
    private Pair<MediaPlayerApi, OnMediaCompletionListener> onMediaCompletionListener;
    @Nullable
    private Pair<MediaPlayerApi, OnMediaErrorListener> onMediaErrorListener;
    @Nullable
    private Pair<MediaPlayerApi, OnMediaSeekCompletionListener> onMediaSeekCompletionListener;

    /*************************
     * MediaPlayer Listeners *
     *************************/

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (onMediaCompletionListener != null) {
            onMediaCompletionListener.second.onCompletion(onMediaCompletionListener.first);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (onMediaPreparedListener != null) {
            onMediaPreparedListener.second.onPrepared(onMediaPreparedListener.first);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return onMediaErrorListener != null && onMediaErrorListener.second.onError(onMediaErrorListener.first);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (onMediaSeekCompletionListener != null) {
            onMediaSeekCompletionListener.second.onSeekComplete(onMediaSeekCompletionListener.first);
        }
    }

    /**************************
     * PlaylistCore Listeners *
     **************************/

    void setOnMediaPreparedListener(MediaPlayerApi mediaPlayerApi, OnMediaPreparedListener listener) {
        onMediaPreparedListener = new Pair<>(mediaPlayerApi, listener);
    }

    void setOnMediaCompletionListener(MediaPlayerApi mediaPlayerApi, OnMediaCompletionListener listener) {
        onMediaCompletionListener = new Pair<>(mediaPlayerApi, listener);
    }

    void setOnMediaErrorListener(MediaPlayerApi mediaPlayerApi, OnMediaErrorListener listener) {
        onMediaErrorListener = new Pair<>(mediaPlayerApi, listener);
    }

    void setOnMediaSeekCompletionListener(MediaPlayerApi mediaPlayerApi, OnMediaSeekCompletionListener listener) {
        onMediaSeekCompletionListener = new Pair<>(mediaPlayerApi, listener);
    }
}
