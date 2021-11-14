package com.devbrackets.android.playlistcoredemo.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.devbrackets.android.playlistcore.components.image.ImageProvider;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;


public class MediaImageProvider implements ImageProvider<MediaItem> {
    interface OnImageUpdatedListener {
        void onImageUpdated();
    }

    @NonNull
    private RequestManager glide;
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
        glide = Glide.with(context.getApplicationContext());
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
    public void updateImages(@NonNull MediaItem playlistItem) {
        glide.asBitmap().load(playlistItem.getThumbnailUrl()).into(notificationImageTarget);
        glide.asBitmap().load(playlistItem.getArtworkUrl()).into(remoteViewImageTarget);
    }

    /**
     * A class used to listen to the loading of the large notification images and perform
     * the correct functionality to update the notification once it is loaded.
     * <p>
     * <b>NOTE:</b> This is a Glide Image loader class
     */
    private class NotificationImageTarget extends SimpleTarget<Bitmap> {
        @Override
        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            notificationImage = resource;
            listener.onImageUpdated();
        }
    }

    /**
     * A class used to listen to the loading of the large lock screen images and perform
     * the correct functionality to update the artwork once it is loaded.
     * <p>
     * <b>NOTE:</b> This is a Glide Image loader class
     */
    private class RemoteViewImageTarget extends SimpleTarget<Bitmap> {
        @Override
        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            artworkImage = resource;
            listener.onImageUpdated();
        }
    }
}
