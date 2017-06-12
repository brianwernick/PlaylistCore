package com.devbrackets.android.playlistcoredemo.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.helper.AudioFocusHelper;
import com.devbrackets.android.playlistcore.service.BasePlaylistService;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.helper.AudioApi;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;
import com.devbrackets.android.playlistcoredemo.ui.activity.StartupActivity;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

/**
 * A simple service that extends {@link BasePlaylistService} in order to provide
 * the application specific information required.
 */
public class MediaService extends BasePlaylistService<MediaItem, PlaylistManager> implements AudioFocusHelper.AudioFocusCallback {
    private static final int NOTIFICATION_ID = 468; //Arbitrary
    private static final int FOREGROUND_REQUEST_CODE = 246; //Arbitrary
    private static final float AUDIO_DUCK_VOLUME = 0.1f;

    private NotificationImageTarget notificationImageTarget = new NotificationImageTarget();
    private RemoteViewImageTarget remoteViewImageTarget = new RemoteViewImageTarget();

    //Picasso is an image loading library (NOTE: google now recommends using glide for image loading)
    private Picasso picasso;

    @Override
    protected void onServiceCreate() {
        super.onServiceCreate();
        picasso = Picasso.with(getApplicationContext());

        setDefaultLargeNotificationImage(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        getMediaPlayers().add(new AudioApi(getApplicationContext(), new MediaPlayer()));
    }

    @Override
    protected int getNotificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    protected float getAudioDuckVolume() {
        return AUDIO_DUCK_VOLUME;
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

    @Override
    protected int getNotificationIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Override
    protected int getRemoteViewIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Override
    protected void updateLargeNotificationImage(int size, @NonNull MediaItem playlistItem) {
        picasso.load(playlistItem.getThumbnailUrl()).into(notificationImageTarget);
    }

    @Override
    protected void updateRemoteViewArtwork(@NonNull MediaItem playlistItem) {
        picasso.load(playlistItem.getArtworkUrl()).into(remoteViewImageTarget);
    }

    /**
     * A class used to listen to the loading of the large notification images and perform
     * the correct functionality to update the notification once it is loaded.
     *
     * <b>NOTE:</b> This is a Picasso Image loader class
     */
    private class NotificationImageTarget implements Target {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            setLargeNotificationImage(bitmap);
            onLargeNotificationImageUpdated();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            setLargeNotificationImage(null);
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            //Purposefully left blank
        }
    }

    /**
     * A class used to listen to the loading of the large remote view images and perform
     * the correct functionality to update the artwork once it is loaded.
     *
     * <b>NOTE:</b> This is a Picasso Image loader class
     */
    private class RemoteViewImageTarget implements Target {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            setRemoteViewArtwork(bitmap);
            onRemoteViewArtworkUpdated();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            setRemoteViewArtwork(null);
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            //Purposefully left blank
        }
    }
}