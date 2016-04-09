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

package com.devbrackets.android.playlistcore.helper;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.widget.RemoteViews;

import com.devbrackets.android.playlistcore.R;
import com.devbrackets.android.playlistcore.service.RemoteActions;

/**
 * A class to help simplify notification creation and modification for
 * media playback applications.
 */
public class NotificationHelper {
    @NonNull
    protected Context context;
    @Nullable
    protected Class<? extends Service> mediaServiceClass;

    @Nullable
    protected NotificationManager notificationManager;
    @NonNull
    protected NotificationInfo notificationInfo = new NotificationInfo();

    public NotificationHelper(@NonNull Context context) {
        this.context = context;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Dismisses and removes references to any notifications, images, and information
     * associated with current notifications.
     */
    public void release() {
        dismiss();

        mediaServiceClass = null;
        notificationInfo.clean();
    }

    /**
     * Dismisses the current active notification
     */
    public void dismiss() {
        if (notificationManager != null) {
            notificationManager.cancel(notificationInfo.getNotificationId());
        }
    }

    /**
     * Sets weather notifications are shown when audio is playing or
     * ready for playback (e.g. paused).  The notification information
     * will need to be updated by calling {@link #setNotificationBaseInformation(int, int)}
     * and {@link #updateNotificationInformation(String, String, String, Bitmap, Bitmap)} and can be retrieved
     * with {@link #getNotification(android.app.PendingIntent, Class)}
     *
     * @param enabled True if notifications should be shown
     */
    public void setNotificationsEnabled(boolean enabled) {
        if (enabled == notificationInfo.getShowNotifications()) {
            return;
        }

        notificationInfo.setShowNotifications(enabled);

        //Remove the notification when disabling
        if (!enabled && notificationManager != null) {
            notificationManager.cancel(notificationInfo.getNotificationId());
        }
    }

    /**
     * Sets the basic information for the notification that doesn't need to be updated.  To enable the big
     * notification you will need to use {@link #setNotificationBaseInformation(int, int, Class)} instead
     *
     * @param notificationId The ID to specify this notification
     * @param appIcon The applications icon resource
     */
    public void setNotificationBaseInformation(int notificationId, @DrawableRes int appIcon) {
        setNotificationBaseInformation(notificationId, appIcon, null);
    }

    /**
     * Sets the basic information for the notification that doesn't need to be updated.  Additionally, when
     * the mediaServiceClass is set the big notification will send intents to that service to notify of
     * button clicks.  These intents will have an action from
     * <ul>
     *     <li>{@link RemoteActions#ACTION_STOP}</li>
     *     <li>{@link RemoteActions#ACTION_PLAY_PAUSE}</li>
     *     <li>{@link RemoteActions#ACTION_PREVIOUS}</li>
     *     <li>{@link RemoteActions#ACTION_NEXT}</li>
     * </ul>
     *
     * @param notificationId The ID to specify this notification
     * @param appIcon The applications icon resource
     * @param mediaServiceClass The class for the service to notify of big notification actions
     */
    public void setNotificationBaseInformation(int notificationId, @DrawableRes int appIcon, @Nullable Class<? extends Service> mediaServiceClass) {
        notificationInfo.setNotificationId(notificationId);
        notificationInfo.setAppIcon(appIcon);
        this.mediaServiceClass = mediaServiceClass;
    }

    /**
     * Sets the volatile information for the notification.  This information is expected to
     * change frequently.
     *
     * @param title The title to display on the notification (e.g. A song name)
     * @param album The album to display on the notification.  This is the second row of text displayed
     * @param artist The artist to display on the notification.  This is the third row of text displayed
     * @param notificationImage An image to display on the notification (e.g. Album artwork)
     * @param secondaryNotificationImage An image to display on the notification should be used to indicate playback type (e.g. Chromecast)
     */
    public void updateNotificationInformation(@Nullable String title, @Nullable String album, @Nullable String artist, @Nullable Bitmap notificationImage,
                                              @Nullable Bitmap secondaryNotificationImage) {
        updateNotificationInformation(title, album, artist, notificationImage, secondaryNotificationImage, null);
    }

    /**
     * Sets the {@link PendingIntent} to call when the notification is clicked.
     *
     * @param pendingIntent The pending intent to use when the notification itself is clicked
     */
    public void setClickPendingIntent(@Nullable PendingIntent pendingIntent) {
        notificationInfo.setPendingIntent(pendingIntent);
    }

    /**
     * Sets the volatile information for the notification.  This information is expected to
     * change frequently.
     *
     * @param title The title to display on the notification (e.g. A song name)
     * @param album The album to display on the notification.  This is the second row of text displayed
     * @param artist The artist to display on the notification.  This is the third row of text displayed
     * @param notificationImage An image to display on the notification (e.g. Album artwork)
     * @param secondaryNotificationImage An image to display on the notification should be used to indicate playback type (e.g. Chromecast)
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    public void updateNotificationInformation(@Nullable String title, @Nullable String album, @Nullable String artist, @Nullable Bitmap notificationImage,
                                              @Nullable Bitmap secondaryNotificationImage, @Nullable NotificationMediaState notificationMediaState) {
        notificationInfo.setTitle(title);
        notificationInfo.setAlbum(album);
        notificationInfo.setArtist(artist);
        notificationInfo.setLargeImage(notificationImage);
        notificationInfo.setSecondaryImage(secondaryNotificationImage);
        notificationInfo.setMediaState(notificationMediaState);

        if (notificationInfo.getShowNotifications() && notificationManager != null && mediaServiceClass != null) {
            Notification notification = getNotification(notificationInfo.getPendingIntent(), mediaServiceClass);
            notificationManager.notify(notificationInfo.getNotificationId(), notification);
        }
    }

    /**
     * Returns a fully constructed notification to use when moving a service to the
     * foreground.  This should be called after the notification information is set with
     * {@link #setNotificationBaseInformation(int, int)} and {@link #updateNotificationInformation(String, String, String, Bitmap, Bitmap)}.
     *
     * @param pendingIntent The pending intent to use when the notification itself is clicked
     * @return The constructed notification
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public Notification getNotification(@Nullable PendingIntent pendingIntent, @NonNull Class<? extends Service> serviceClass) {
        setClickPendingIntent(pendingIntent);
        RemoteViews customNotificationViews = getCustomNotification(serviceClass);

        boolean allowSwipe = notificationInfo.getMediaState() == null || !notificationInfo.getMediaState().isPlaying();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContent(customNotificationViews);
        builder.setContentIntent(pendingIntent);
        builder.setDeleteIntent(createPendingIntent(RemoteActions.ACTION_STOP, serviceClass));
        builder.setSmallIcon(notificationInfo.getAppIcon());
        builder.setAutoCancel(allowSwipe);
        builder.setOngoing(!allowSwipe);

        if (pendingIntent != null) {
            customNotificationViews.setOnClickPendingIntent(R.id.playlistcore_notification_touch_area, pendingIntent);
        }

        //Set the notification category on lollipop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_STATUS);
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        //Build the notification and set the expanded content view if there is a service to inform of clicks
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && mediaServiceClass != null) {
            notification.bigContentView = getBigNotification(serviceClass);
            notification.bigContentView.setOnClickPendingIntent(R.id.playlistcore_big_notification_touch_area, pendingIntent);
        }

        return notification;
    }

    /**
     * Creates the RemoteViews used for the custom (standard) notification
     *
     * @return The resulting RemoteViews
     */
    @NonNull
    protected RemoteViews getCustomNotification(@NonNull Class<? extends Service> serviceClass) {
        RemoteViews customNotification = new RemoteViews(context.getPackageName(), R.layout.playlistcore_notification_content);

        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_playpause, createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass));
        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_next, createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass));
        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_prev, createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass));

        customNotification.setTextViewText(R.id.playlistcore_notification_title, notificationInfo.getTitle());
        customNotification.setTextViewText(R.id.playlistcore_notification_album, notificationInfo.getAlbum());
        customNotification.setTextViewText(R.id.playlistcore_notification_artist, notificationInfo.getArtist());
        if (notificationInfo.getLargeImage() != null) {
            customNotification.setBitmap(R.id.playlistcore_notification_large_image, "setImageBitmap", notificationInfo.getLargeImage());
        }

        if (notificationInfo.getMediaState() != null) {
            updateCustomNotificationMediaState(customNotification);
        }

        return customNotification;
    }

    /**
     * Creates the RemoteViews used for the expanded (big) notification
     *
     * @return The resulting RemoteViews
     */
    @NonNull
    protected RemoteViews getBigNotification(Class<? extends Service> serviceClass) {
        RemoteViews bigContent = new RemoteViews(context.getPackageName(), R.layout.playlistcore_big_notification_content);

        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_close, createPendingIntent(RemoteActions.ACTION_STOP, serviceClass));
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_playpause, createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass));
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_next, createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass));
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_prev, createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass));

        bigContent.setTextViewText(R.id.playlistcore_big_notification_title, notificationInfo.getTitle());
        bigContent.setTextViewText(R.id.playlistcore_big_notification_album, notificationInfo.getAlbum());
        bigContent.setTextViewText(R.id.playlistcore_big_notification_artist, notificationInfo.getArtist());
        bigContent.setBitmap(R.id.playlistcore_big_notification_large_image, "setImageBitmap", notificationInfo.getLargeImage());
        bigContent.setBitmap(R.id.playlistcore_big_notification_secondary_image, "setImageBitmap", notificationInfo.getSecondaryImage());

        //Makes sure the play/pause, next, and previous are displayed correctly
        if (notificationInfo.getMediaState() != null) {
            updateBigNotificationMediaState(bigContent);
        }

        return bigContent;
    }

    /**
     * Updates the images for the play/pause button so that only valid ones are
     * displayed with the correct state.
     *
     * @param customNotification The RemoteViews to use to modify the state
     */
    protected void updateCustomNotificationMediaState(@Nullable RemoteViews customNotification) {
        NotificationMediaState state = notificationInfo.getMediaState();
        if (customNotification == null || state == null) {
            return;
        }

        customNotification.setImageViewResource(R.id.playlistcore_notification_playpause, state.isPlaying() ? R.drawable.playlistcore_notification_pause : R.drawable.playlistcore_notification_play);
        customNotification.setInt(R.id.playlistcore_notification_prev, "setVisibility", state.isPreviousEnabled() ? View.VISIBLE : View.GONE);
        customNotification.setInt(R.id.playlistcore_notification_next, "setVisibility", state.isNextEnabled() ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the images for the play/pause, next, and previous buttons so that only valid ones are
     * displayed with the correct state.
     *
     * @param bigContent The RemoteViews to use to modify the state
     */
    protected void updateBigNotificationMediaState(@Nullable RemoteViews bigContent) {
        NotificationMediaState state = notificationInfo.getMediaState();
        if (bigContent == null || state == null) {
            return;
        }

        bigContent.setImageViewResource(R.id.playlistcore_big_notification_playpause, state.isPlaying() ? R.drawable.playlistcore_notification_pause : R.drawable.playlistcore_notification_play);
        bigContent.setInt(R.id.playlistcore_big_notification_prev, "setVisibility", state.isPreviousEnabled() ? View.VISIBLE : View.INVISIBLE);
        bigContent.setInt(R.id.playlistcore_big_notification_next, "setVisibility", state.isNextEnabled() ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    @NonNull
    protected PendingIntent createPendingIntent(@NonNull String action, @NonNull Class<? extends Service> serviceClass) {
        Intent intent = new Intent(context, serviceClass);
        intent.setAction(action);

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static class NotificationMediaState {
        private boolean isPlaying;
        private boolean isPreviousEnabled;
        private boolean isNextEnabled;

        public boolean isPlaying() {
            return isPlaying;
        }

        public boolean isPreviousEnabled() {
            return isPreviousEnabled;
        }

        public boolean isNextEnabled() {
            return isNextEnabled;
        }

        public void setPlaying(boolean isPlaying) {
            this.isPlaying = isPlaying;
        }

        public void setPreviousEnabled(boolean isPreviousEnabled) {
            this.isPreviousEnabled = isPreviousEnabled;
        }

        public void setNextEnabled(boolean isNextEnabled) {
            this.isNextEnabled = isNextEnabled;
        }
    }
}
