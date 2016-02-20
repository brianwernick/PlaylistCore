/*
 * Copyright (C) 2016 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.service;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.devbrackets.android.playlistcore.R;
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper;
import com.devbrackets.android.playlistcore.helper.NotificationHelper;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcore.manager.IPlaylistItem;

/**
 * A base service for adding media playback support using the {@link BasePlaylistManager}.
 * <p>
 * <b>NOTE:</b> This service will request a wifi wakelock if the item
 * being played isn't downloaded (see {@link #isDownloaded(IPlaylistItem)}).
 * <p>
 * Additionally, the manifest permission &lt;uses-permission android:name="android.permission.WAKE_LOCK" /&gt;
 * should be requested to avoid interrupted playback.
 */
@SuppressWarnings("unused")
public abstract class BasePlaylistService<I extends IPlaylistItem, M extends BasePlaylistManager<I>> extends PlaylistServiceCore<I, M> {
    private static final String TAG = "BasePlaylistService";

    @Nullable
    protected NotificationHelper notificationHelper;
    @Nullable
    protected MediaControlsHelper mediaControlsHelper;

    @Nullable
    protected String currentLargeNotificationUrl;
    @Nullable
    protected String currentLockScreenArtworkUrl;

    protected boolean foregroundSetup;
    protected boolean notificationSetup;

    /**
     * Retrieves the ID to use for the notification and registering this
     * service as Foreground when media is playing. (Foreground is removed
     * when paused)
     *
     * @return The ID to use for the notification
     */
    protected abstract int getNotificationId();

    /**
     * Returns the PendingIntent to use when the playback notification is clicked.
     * This is called when the playback is started initially to setup the notification
     * and the service as Foreground.
     *
     * @return The PendingIntent to use when the notification is clicked
     */
    @NonNull
    protected abstract PendingIntent getNotificationClickPendingIntent();

    /**
     * Retrieves the Image to use for the large notification (the double tall notification)
     * when {@link #getLargeNotificationImage()} returns null.
     *
     * @return The image to use on the large notification when no other one is provided
     */
    @Nullable
    protected abstract Bitmap getDefaultLargeNotificationImage();

    /**
     * Retrieves the Drawable resource that specifies the icon to place in the
     * status bar for the media playback notification.
     *
     * @return The Drawable resource id
     */
    @DrawableRes
    protected abstract int getNotificationIconRes();

    /**
     * Retrieves the Drawable resource that specifies the icon to place on the
     * lock screen to indicate the app the owns the content being displayed.
     *
     * @return The Drawable resource id
     */
    @DrawableRes
    protected abstract int getRemoteViewIconRes();

    /**
     * Retrieves the image that will be displayed in the notification to represent
     * the currently playing item.
     *
     * @return The image to display in the notification or null
     */
    @Nullable
    protected Bitmap getLargeNotificationImage() {
        return null;
    }

    /**
     * Retrieves the image that will be displayed in the notification as a secondary
     * image.  This can be used to specify playback type (e.g. Chromecast).
     * <p>
     * This will be called any time the notification is updated
     *
     * @return The image to display in the secondary position
     */
    @Nullable
    protected Bitmap getLargeNotificationSecondaryImage() {
        return null;
    }

    /**
     * Retrieves the image that will be displayed in the notification as a secondary
     * image if {@link #getLargeNotificationSecondaryImage()} returns null.
     *
     * @return The fallback image to display in the secondary position
     */
    @Nullable
    protected Bitmap getDefaultLargeNotificationSecondaryImage() {
        return null;
    }

    /**
     * Called when the image in the notification needs to be updated.
     *
     * @param size The square size for the image to display
     * @param playlistItem The media item to get the image for
     */
    protected void updateLargeNotificationImage(int size, I playlistItem) {
        //Purposefully left blank
    }

    /**
     * Retrieves the image that will be displayed as the lock screen artwork
     * for the currently playing item.
     *
     * @return The image to display on the lock screen
     */
    @Nullable
    protected Bitmap getRemoteViewArtwork() {
        return null;
    }

    /**
     * Called when the image for the Lock Screen needs to be updated.
     *
     * @param playlistItem The playlist item to get the lock screen image for
     */
    protected void updateRemoteViewArtwork(I playlistItem) {
        //Purposefully left blank
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        notificationHelper = null;
        mediaControlsHelper = null;
    }

    @Override
    protected void onServiceCreate() {
        super.onServiceCreate();

        notificationHelper = new NotificationHelper(getApplicationContext());
        mediaControlsHelper = new MediaControlsHelper(getApplicationContext(), getClass());
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the Large Notification.
     */
    protected void onLargeNotificationImageUpdated() {
        updateNotification();
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the LockScreen Artwork.
     */
    protected void onLockScreenArtworkUpdated() {
        updateRemoteViews();
    }

    /**
     * Sets up the service as a Foreground service only if we aren't already registered as such
     */
    @Override
    protected void setupForeground() {
        if (!foregroundSetup && notificationSetup && notificationHelper != null) {
            foregroundSetup = true;
            startForeground(getNotificationId(), notificationHelper.getNotification(getNotificationClickPendingIntent(), getClass()));
        }
    }

    /**
     * If the service is registered as a foreground service then it will be unregistered
     * as such without removing the notification
     */
    @Override
    protected void stopForeground() {
        if (foregroundSetup) {
            foregroundSetup = false;
            stopForeground(false);
        }
    }

    @Override
    protected void relaxResources(boolean releaseAudioPlayer) {
        super.relaxResources(releaseAudioPlayer);
        stopForeground(true);
        foregroundSetup = false;

        if (notificationHelper != null) {
            notificationHelper.release();
        }

        if (mediaControlsHelper != null) {
            mediaControlsHelper.release();
        }

        notificationSetup = false;
    }

    /**
     * Requests the service be transferred to the foreground, initializing the
     * LockScreen and Notification helpers for playback control.
     */
    @Override
    protected void setupAsForeground() {
        //Sets up the Lock Screen playback controls
        if (mediaControlsHelper != null) {
            mediaControlsHelper.setMediaControlsEnabled(true);
            mediaControlsHelper.setBaseInformation(getRemoteViewIconRes());
        }

        //Sets up the Notifications
        if (notificationHelper != null) {
            notificationHelper.setNotificationsEnabled(true);
            notificationHelper.setNotificationBaseInformation(getNotificationId(), getNotificationIconRes(), getClass());
        }

        //Starts the service as the foreground audio player
        notificationSetup = true;
        setupForeground();

        updateRemoteViews();
        updateNotification();
    }

    /**
     * Performs the process to update the playback controls and images in the notification
     * associated with the current playlist item.
     */
    @Override
    protected void updateNotification() {
        if (currentPlaylistItem == null || !notificationSetup || notificationHelper == null) {
            return;
        }

        //Generate the notification state
        NotificationHelper.NotificationMediaState mediaState = new NotificationHelper.NotificationMediaState();
        mediaState.setNextEnabled(getPlaylistManager().isNextAvailable());
        mediaState.setPreviousEnabled(getPlaylistManager().isPreviousAvailable());
        mediaState.setPlaying(isPlaying());


        //Update the big notification images
        Bitmap bitmap = getLargeNotificationImage();
        if (bitmap == null) {
            bitmap = getDefaultLargeNotificationImage();
        }

        Bitmap secondaryImage = getLargeNotificationSecondaryImage();
        if (secondaryImage == null) {
            secondaryImage = getDefaultLargeNotificationSecondaryImage();
        }

        //Finish up the update
        String title = currentPlaylistItem.getTitle();
        String album = currentPlaylistItem.getAlbum();
        String artist = currentPlaylistItem.getArtist();
        notificationHelper.setClickPendingIntent(getNotificationClickPendingIntent());
        notificationHelper.updateNotificationInformation(title, album, artist, bitmap, secondaryImage, mediaState);
    }

    /**
     * Performs the process to update the playback controls and the background
     * (artwork) image displayed on the lock screen and other remote views.
     */
    @Override
    protected void updateRemoteViews() {
        if (currentPlaylistItem == null || !notificationSetup || mediaControlsHelper == null) {
            return;
        }

        //Generate the notification state
        NotificationHelper.NotificationMediaState mediaState = new NotificationHelper.NotificationMediaState();
        mediaState.setNextEnabled(getPlaylistManager().isNextAvailable());
        mediaState.setPreviousEnabled(getPlaylistManager().isPreviousAvailable());
        mediaState.setPlaying(isPlaying());


        //Finish up the update
        String title = currentPlaylistItem.getTitle();
        String album = currentPlaylistItem.getAlbum();
        String artist = currentPlaylistItem.getArtist();
        mediaControlsHelper.update(title, album, artist, getRemoteViewArtwork(), mediaState);
    }

    @Override
    protected void mediaItemChanged() {
        super.mediaItemChanged();

        //Starts the notification loading
        if (currentPlaylistItem != null && (currentLargeNotificationUrl == null || !currentLargeNotificationUrl.equals(currentPlaylistItem.getThumbnailUrl()))) {
            int size = getResources().getDimensionPixelSize(R.dimen.playlistcore_big_notification_height);
            updateLargeNotificationImage(size, currentPlaylistItem);
            currentLargeNotificationUrl = currentPlaylistItem.getThumbnailUrl();
        }

        //Starts the lock screen loading
        if (currentPlaylistItem != null && (currentLockScreenArtworkUrl == null || !currentLockScreenArtworkUrl.equalsIgnoreCase(currentPlaylistItem.getArtworkUrl()))) {
            updateRemoteViewArtwork(currentPlaylistItem);
            currentLockScreenArtworkUrl = currentPlaylistItem.getArtworkUrl();
        }
    }
}