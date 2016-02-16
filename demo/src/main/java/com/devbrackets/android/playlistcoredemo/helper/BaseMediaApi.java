package com.devbrackets.android.playlistcoredemo.helper;

import android.media.MediaPlayer;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.listener.OnMediaBufferUpdateListener;
import com.devbrackets.android.playlistcore.listener.OnMediaCompletionListener;
import com.devbrackets.android.playlistcore.listener.OnMediaErrorListener;
import com.devbrackets.android.playlistcore.listener.OnMediaPreparedListener;
import com.devbrackets.android.playlistcore.listener.OnMediaSeekCompletionListener;

public abstract class BaseMediaApi implements MediaPlayerApi, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnBufferingUpdateListener {

    //The listeners that can be registered
    protected OnMediaPreparedListener preparedListener;
    protected OnMediaCompletionListener completionListener;
    protected OnMediaSeekCompletionListener seekCompletionListener;
    protected OnMediaErrorListener errorListener;
    protected OnMediaBufferUpdateListener bufferUpdateListener;

    protected boolean prepared;
    protected int bufferPercent;

    //The listener registrations
    @Override
    public void setOnMediaPreparedListener(OnMediaPreparedListener listener) {
        preparedListener = listener;
    }

    @Override
    public void setOnMediaBufferUpdateListener(OnMediaBufferUpdateListener listener) {
        bufferUpdateListener = listener;
    }

    @Override
    public void setOnMediaSeekCompletionListener(OnMediaSeekCompletionListener listener) {
        seekCompletionListener = listener;
    }

    @Override
    public void setOnMediaCompletionListener(OnMediaCompletionListener listener) {
        completionListener = listener;
    }

    @Override
    public void setOnMediaErrorListener(OnMediaErrorListener listener) {
        errorListener = listener;
    }

    //The listeners from the MediaPlayer (and VideoView)
    @Override
    public void onCompletion(MediaPlayer mp) {
        if (completionListener != null) {
            completionListener.onCompletion(this);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;

        if (preparedListener != null) {
            preparedListener.onPrepared(this);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return errorListener != null && errorListener.onError(this);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (seekCompletionListener != null) {
            seekCompletionListener.onSeekComplete(this);
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferPercent = percent;

        if (bufferUpdateListener != null) {
            bufferUpdateListener.onBufferingUpdate(this, percent);
        }
    }
}
