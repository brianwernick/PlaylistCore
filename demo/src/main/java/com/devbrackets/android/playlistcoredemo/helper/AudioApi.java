package com.devbrackets.android.playlistcoredemo.helper;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;

import org.jetbrains.annotations.NotNull;

public class AudioApi extends BaseMediaApi {
    @NonNull
    private Context context;
    @NonNull
    private MediaPlayer audioPlayer;

    public AudioApi(@NonNull Context context, @NonNull MediaPlayer audioPlayer) {
        this.context = context.getApplicationContext();
        this.audioPlayer = audioPlayer;

        audioPlayer.setOnErrorListener(this);
        audioPlayer.setOnPreparedListener(this);
        audioPlayer.setOnCompletionListener(this);
        audioPlayer.setOnSeekCompleteListener(this);
        audioPlayer.setOnBufferingUpdateListener(this);

        audioPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
        } else {
            audioPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
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
    public boolean getHandlesOwnAudioFocus() {
        return false;
    }

    @Override
    public boolean handlesItem(@NotNull MediaItem item) {
        return item.getMediaType() == BasePlaylistManager.AUDIO;
    }

    @Override
    public void playItem(@NotNull MediaItem item) {
        try {
            prepared = false;
            bufferPercent = 0;
            audioPlayer.setDataSource(context, Uri.parse(item.getDownloaded() ? item.getDownloadedMediaUri() : item.getMediaUrl()));
            audioPlayer.prepareAsync();
        } catch (Exception e) {
            //Purposefully left blank
        }
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
