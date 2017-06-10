package com.devbrackets.android.playlistcoredemo.helper;

import android.media.MediaPlayer;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.listener.MediaStatusListener;

import org.jetbrains.annotations.NotNull;

public abstract class BaseMediaApi implements MediaPlayerApi,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnBufferingUpdateListener {

    protected boolean prepared;
    protected int bufferPercent;

    protected MediaStatusListener mediaStatusListener;

    @Override
    public void setMediaStatusListener(@NotNull MediaStatusListener listener) {
        mediaStatusListener = listener;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mediaStatusListener != null) {
            mediaStatusListener.onCompletion(this);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;

        if (mediaStatusListener != null) {
            mediaStatusListener.onPrepared(this);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return mediaStatusListener != null && mediaStatusListener.onError(this);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (mediaStatusListener != null) {
            mediaStatusListener.onSeekComplete(this);
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferPercent = percent;

        if (mediaStatusListener != null) {
            mediaStatusListener.onBufferingUpdate(this, percent);
        }
    }
}
