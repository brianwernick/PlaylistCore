package com.devbrackets.android.playlistcoredemo.helper;

import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.widget.VideoView;

import com.devbrackets.android.playlistcore.api.VideoPlayerApi;

public class VideoApi extends BaseMediaApi implements VideoPlayerApi {
    private VideoView videoView;

    public VideoApi(VideoView videoView) {
        this.videoView = videoView;

        videoView.setOnErrorListener(this);
        videoView.setOnPreparedListener(this);
        videoView.setOnCompletionListener(this);
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
        prepared = false;
        bufferPercent = 0;
        videoView.setVideoURI(uri);
    }

    @Override
    public long getCurrentPosition() {
        return prepared ? videoView.getCurrentPosition() : 0;
    }

    @Override
    public long getDuration() {
        return prepared ? videoView.getDuration() : 0;
    }

    @Override
    public int getBufferedPercent() {
        return bufferPercent;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        super.onPrepared(mp);

        //Registers the rest of the listeners we need the MediaPlayer for
        mp.setOnSeekCompleteListener(this);
        mp.setOnBufferingUpdateListener(this);
    }
}