package com.devbrackets.android.playlistcoredemo.service;

import android.media.MediaPlayer;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.service.BasePlaylistService;
import com.devbrackets.android.playlistcore.service.DefaultPlaylistHandler;
import com.devbrackets.android.playlistcore.service.PlaylistHandler;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.helper.AudioApi;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;

import org.jetbrains.annotations.NotNull;

/**
 * A simple service that extends {@link BasePlaylistService} in order to provide
 * the application specific information required.
 */
public class MediaService extends BasePlaylistService<MediaItem, PlaylistManager> {
    @Override
    public void onCreate() {
        super.onCreate();
        getPlaylistHandler().getMediaPlayers().add(new AudioApi(getApplicationContext(), new MediaPlayer()));
    }

    @NonNull
    @Override
    protected PlaylistManager getPlaylistManager() {
        return App.getPlaylistManager();
    }

    @NotNull
    @Override
    public PlaylistHandler<MediaItem> newPlaylistHandler() {
        MediaImageProvider imageProvider = new MediaImageProvider(getApplicationContext(), new MediaImageProvider.OnImageUpdatedListener() {
            @Override
            public void onImageUpdated() {
                getPlaylistHandler().updateMediaControls();
            }
        });

        return new DefaultPlaylistHandler<MediaItem, PlaylistManager>(
                getApplicationContext(),
                getClass(),
                getPlaylistManager(),
                imageProvider
        );
    }
}