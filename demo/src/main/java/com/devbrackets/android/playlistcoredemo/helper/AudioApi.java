package com.devbrackets.android.playlistcoredemo.helper;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.api.AudioPlayerApi;

public class AudioApi implements AudioPlayerApi, MediaPlayer.OnBufferingUpdateListener {
    private MediaPlayer audioPlayer;
    private int bufferPercent = 0;

    public AudioApi(MediaPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        audioPlayer.setOnBufferingUpdateListener(this);
    }

    @Override
    public boolean isPlaying() {
        return audioPlayer.isPlaying();
    }

    @Override
    public void play() {
        audioPlayer.start();
    }

    @Override
    public void pause() {
        audioPlayer.pause();
    }

    @Override
    public void stop() {
        audioPlayer.stop();
    }

    @Override
    public void reset() {
        audioPlayer.reset();
    }

    @Override
    public void release() {
        audioPlayer.release();
    }

    @Override
    public void setVolume(@FloatRange(from = 0.0, to = 1.0) float left, @FloatRange(from = 0.0, to = 1.0) float right) {
        audioPlayer.setVolume(left, right);
    }

    @Override
    public void seekTo(@IntRange(from = 0L) long milliseconds) {
        audioPlayer.seekTo((int)milliseconds);
    }

    @Override
    public void setStreamType(int streamType) {
        audioPlayer.setAudioStreamType(streamType);
    }

    @Override
    public void setWakeMode(Context context, int mode) {
        audioPlayer.setWakeMode(context, mode);
    }

    @Override
    public void setDataSource(@NonNull Context context, @NonNull Uri uri) {
        try {
            audioPlayer.setDataSource(context, uri);
        } catch (Exception e) {
            //Purposefully left blank
        }
    }

    @Override
    public void prepareAsync() {
        audioPlayer.prepareAsync();
    }

    @Override
    public long getCurrentPosition() {
        return audioPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return audioPlayer.getDuration();
    }

    @Override
    public int getBufferedPercent() {
        return bufferPercent;
    }

    @Override
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener onPreparedListener) {
        audioPlayer.setOnPreparedListener(onPreparedListener);
    }

    @Override
    public void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener onBufferingUpdateListener) {
        //Purposefully left blank
    }

    @Override
    public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener onSeekCompleteListener) {
        //Purposefully left blank
    }

    @Override
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener onCompletionListener) {
        audioPlayer.setOnCompletionListener(onCompletionListener);
    }

    @Override
    public void setOnErrorListener(MediaPlayer.OnErrorListener onErrorListener) {
        audioPlayer.setOnErrorListener(onErrorListener);
    }

    @Override // From MediaPlayer.OnBufferingUpdateListener
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        bufferPercent = percent;
    }
}
