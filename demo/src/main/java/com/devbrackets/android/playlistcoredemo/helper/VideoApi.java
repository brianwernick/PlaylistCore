package com.devbrackets.android.playlistcoredemo.helper;

import android.net.Uri;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.widget.VideoView;

import com.devbrackets.android.playlistcore.api.VideoPlayerApi;
import com.devbrackets.android.playlistcore.listener.OnMediaBufferUpdateListener;
import com.devbrackets.android.playlistcore.listener.OnMediaCompletionListener;
import com.devbrackets.android.playlistcore.listener.OnMediaErrorListener;
import com.devbrackets.android.playlistcore.listener.OnMediaPreparedListener;
import com.devbrackets.android.playlistcore.listener.OnMediaSeekCompletionListener;

public class VideoApi implements VideoPlayerApi {
    private VideoView videoView;
    private MediaPlayerListenerShim listenerShim;

    public VideoApi(VideoView videoView) {
        this.videoView = videoView;
        listenerShim = new MediaPlayerListenerShim();
    }

    @Override
    public boolean isPlaying() {
        return videoView.isPlaying();
    }

    @Override
    public void play() {
        videoView.start();
    }

    @Override
    public void pause() {
        videoView.pause();
    }

    @Override
    public void stop() {
        videoView.stopPlayback();
    }

    @Override
    public void reset() {
        videoView.stopPlayback();
        videoView.setVideoURI(null);
    }

    @Override
    public void release() {
        videoView.suspend();
    }

    @Override
    public void setVolume(@FloatRange(from = 0.0, to = 1.0) float left, @FloatRange(from = 0.0, to = 1.0) float right) {
        //Not supported by the VideoView
    }

    @Override
    public void seekTo(@IntRange(from = 0L) long milliseconds) {
        videoView.seekTo((int)milliseconds);
    }

    @Override
    public void setDataSource(@NonNull Uri uri) {
        videoView.setVideoURI(uri);
    }

    @Override
    public long getCurrentPosition() {
        return videoView.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return videoView.getDuration();
    }

    @Override
    public int getBufferedPercent() {
        return videoView.getBufferPercentage();
    }

    @Override
    public void setOnMediaPreparedListener(OnMediaPreparedListener listener) {
        listenerShim.setOnMediaPreparedListener(this, listener);
        videoView.setOnPreparedListener(listenerShim);
    }

    @Override
    public void setOnMediaBufferUpdateListener(OnMediaBufferUpdateListener listener) {
        //Purposefully left blank
    }

    @Override
    public void setOnMediaSeekCompletionListener(OnMediaSeekCompletionListener listener) {
        //Purposefully left blank
    }

    @Override
    public void setOnMediaCompletionListener(OnMediaCompletionListener listener) {
        listenerShim.setOnMediaCompletionListener(this, listener);
        videoView.setOnCompletionListener(listenerShim);
    }

    @Override
    public void setOnMediaErrorListener(OnMediaErrorListener listener) {
        listenerShim.setOnMediaErrorListener(this, listener);
        videoView.setOnErrorListener(listenerShim);
    }
}
