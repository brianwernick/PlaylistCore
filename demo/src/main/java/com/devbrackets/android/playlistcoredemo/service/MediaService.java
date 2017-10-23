package com.devbrackets.android.playlistcoredemo.service;

import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.service.BasePlaylistService;
import com.devbrackets.android.playlistcore.components.playlisthandler.DefaultPlaylistHandler;
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.helper.AudioApi;
import com.devbrackets.android.playlistcoredemo.helper.cast.CastMediaPlayer;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;

/**
 * A simple service that extends {@link BasePlaylistService} in order to provide
 * the application specific information required.
 */
public class MediaService extends BasePlaylistService<MediaItem, PlaylistManager> implements
        CastMediaPlayer.OnConnectionChangeListener,
        CastMediaPlayer.OnMediaInfoChangeListener {

    @Override
    public void onCreate() {
        super.onCreate();

        // Adds the audio player implementation, otherwise there's nothing to play media with
        getPlaylistManager().getMediaPlayers().add(new CastMediaPlayer(getApplicationContext(), this, this));
        getPlaylistManager().getMediaPlayers().add(new AudioApi(getApplicationContext()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Releases and clears all the MediaPlayers
        for (MediaPlayerApi<MediaItem> player : getPlaylistManager().getMediaPlayers()) {
            player.release();
        }

        getPlaylistManager().getMediaPlayers().clear();
    }

    @NonNull
    @Override
    protected PlaylistManager getPlaylistManager() {
        return App.getPlaylistManager();
    }

    @NonNull
    @Override
    public PlaylistHandler<MediaItem> newPlaylistHandler() {
        MediaImageProvider imageProvider = new MediaImageProvider(getApplicationContext(), new MediaImageProvider.OnImageUpdatedListener() {
            @Override
            public void onImageUpdated() {
                getPlaylistHandler().updateMediaControls();
            }
        });

        return new DefaultPlaylistHandler.Builder<>(
                getApplicationContext(),
                getClass(),
                getPlaylistManager(),
                imageProvider
        ).build();
    }

    /**
     * An implementation for the chromecast MediaPlayer {@link CastMediaPlayer} that allows us to inform the
     * {@link PlaylistHandler} that the state changed (which will handle swapping between local and remote playback).
     */
    @Override
    public void onCastMediaPlayerConnectionChange(@NonNull CastMediaPlayer player, @NonNull MediaPlayerApi.RemoteConnectionState state) {
        getPlaylistHandler().onRemoteMediaPlayerConnectionChange(player, state);
    }

    /**
     * An implementation for the chromecast MediaPlayer {@link CastMediaPlayer} that allow us to inform the
     * {@link PlaylistHandler} that the information for the current media item has changed. This will normally
     * be called for state synchronization when we are informed that the media item has actually started, paused,
     * etc.
     */
    @Override
    public void onMediaInfoChange(@NonNull CastMediaPlayer player) {
        getPlaylistHandler().updateMediaControls();
    }
}