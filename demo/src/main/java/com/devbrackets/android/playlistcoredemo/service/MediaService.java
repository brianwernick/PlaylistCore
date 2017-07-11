package com.devbrackets.android.playlistcoredemo.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.helper.AudioFocusHelper;
import com.devbrackets.android.playlistcore.helper.image.ImageProvider;
import com.devbrackets.android.playlistcore.service.BasePlaylistService;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.helper.AudioApi;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;
import com.devbrackets.android.playlistcoredemo.ui.activity.StartupActivity;

import org.jetbrains.annotations.NotNull;

/**
 * A simple service that extends {@link BasePlaylistService} in order to provide
 * the application specific information required.
 */
public class MediaService extends BasePlaylistService<MediaItem, PlaylistManager> implements AudioFocusHelper.AudioFocusCallback {
    private static final int FOREGROUND_REQUEST_CODE = 246; //Arbitrary

    @NotNull
    private ImageProvider<MediaItem> imageProvider;

    @Override
    protected void onServiceCreate() {
        super.onServiceCreate();
        imageProvider = new MediaImageProvider(getApplicationContext(), this);
        getMediaPlayers().add(new AudioApi(getApplicationContext(), new MediaPlayer()));
    }

    @NonNull
    @Override
    protected PlaylistManager getPlaylistManager() {
        return App.getPlaylistManager();
    }

    @NonNull
    @Override
    protected PendingIntent getNotificationClickPendingIntent() {
        Intent intent = new Intent(getApplicationContext(), StartupActivity.class);
        return PendingIntent.getActivity(getApplicationContext(), FOREGROUND_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @NotNull
    @Override
    protected ImageProvider<MediaItem> getImageProvider() {
        return imageProvider;
    }
}