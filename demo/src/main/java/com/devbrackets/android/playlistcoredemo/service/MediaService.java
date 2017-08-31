package com.devbrackets.android.playlistcoredemo.service;

import android.support.annotation.NonNull;

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
public class MediaService extends BasePlaylistService<MediaItem, PlaylistManager> {
    @Override
    public void onCreate() {
        super.onCreate();

        // Adds the audio player implementation, otherwise there's nothing to play media with
        getPlaylistManager().getMediaPlayers().add(new CastMediaPlayer(getApplicationContext()));
        getPlaylistManager().getMediaPlayers().add(new AudioApi(getApplicationContext()));
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
}