package com.devbrackets.android.playlistcoredemo.helper;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.api.AudioPlayerApi;

public class AudioApi extends BaseMediaApi implements AudioPlayerApi{
    private MediaPlayer audioPlayer;

    public AudioApi(MediaPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;

        audioPlayer.setOnErrorListener(this);
        audioPlayer.setOnPreparedListener(this);
        audioPlayer.setOnCompletionListener(this);
        audioPlayer.setOnSeekCompleteListener(this);
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
            prepared = false;
            bufferPercent = 0;
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
        return prepared ? audioPlayer.getCurrentPosition() : 0;
    }

    @Override
    public long getDuration() {
        return prepared ? audioPlayer.getDuration() : 0;
    }

    @Override
    public int getBufferedPercent() {
        return bufferPercent;
    }
}
