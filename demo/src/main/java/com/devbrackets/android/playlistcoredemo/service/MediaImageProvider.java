package com.devbrackets.android.playlistcoredemo.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.components.image.ImageProvider;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MediaImageProvider implements ImageProvider<MediaItem> {
    interface OnImageUpdatedListener {
        void onImageUpdated();
    }

    @NotNull
    private Picasso picasso;
    @NonNull
    private OnImageUpdatedListener listener;

    @NonNull
    private NotificationImageTarget notificationImageTarget = new NotificationImageTarget();
    @NonNull
    private RemoteViewImageTarget remoteViewImageTarget = new RemoteViewImageTarget();

    @NonNull
    private Bitmap defaultNotificationImage;

    @Nullable
    private Bitmap notificationImage;
    @Nullable
    private Bitmap artworkImage;

    public MediaImageProvider(@NonNull Context context, @NonNull OnImageUpdatedListener listener) {
        picasso = Picasso.with(context);
        this.listener = listener;

        defaultNotificationImage = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
    }

    @Override
    public int getNotificationIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Override
    public int getRemoteViewIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Nullable
    @Override
    public Bitmap getLargeNotificationImage() {
        return notificationImage != null ? notificationImage : defaultNotificationImage;
    }

    @Nullable
    @Override
    public Bitmap getRemoteViewArtwork() {
        return artworkImage;
    }

    @Override
    public void updateImages(@NotNull MediaItem playlistItem) {
        picasso.load(playlistItem.getThumbnailUrl()).into(notificationImageTarget);
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
            notificationImage = bitmap;
            listener.onImageUpdated();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            notificationImage = null;
            listener.onImageUpdated();
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
            artworkImage = bitmap;
            listener.onImageUpdated();
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            artworkImage = null;
            listener.onImageUpdated();
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            //Purposefully left blank
        }
    }
}
