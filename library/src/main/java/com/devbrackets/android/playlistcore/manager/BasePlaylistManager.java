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

package com.devbrackets.android.playlistcore.manager;

import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.devbrackets.android.playlistcore.annotation.SupportedMediaType;
import com.devbrackets.android.playlistcore.api.VideoPlayerApi;
import com.devbrackets.android.playlistcore.event.MediaProgress;
import com.devbrackets.android.playlistcore.event.PlaylistItemChange;
import com.devbrackets.android.playlistcore.listener.PlaylistListener;
import com.devbrackets.android.playlistcore.listener.ProgressListener;
import com.devbrackets.android.playlistcore.service.BasePlaylistService;
import com.devbrackets.android.playlistcore.service.PlaylistServiceCore;
import com.devbrackets.android.playlistcore.service.RemoteActions;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A manager to keep track of a playlist of items that a service can use for playback.
 * Additionally, this manager provides methods for interacting with the specified service
 * to simplify and standardize implementations in the service itself.  This manager can be
 * used as standalone with a custom service, or in conjunction with
 * {@link BasePlaylistService}
 */
@SuppressWarnings("unused")
public abstract class BasePlaylistManager<I extends IPlaylistItem> implements PlaylistListener<I>, ProgressListener {
    private static final String TAG = "PlaylistManager";

    public static final int INVALID_ID = -1;
    public static final int INVALID_POSITION = -1;

    /**
     * A flag used to represent either an Audio item or
     * support for Audio items.  This is a flag that is
     * referenced by {@link #allowedTypeFlag} and
     * {@link IPlaylistItem#getMediaType()}
     */
    public static final int AUDIO = 1;

    /**
     * A flag used to represent either a Video item or
     * support for Video items.  This is a flag that is
     * referenced by {@link #allowedTypeFlag} and
     * {@link IPlaylistItem#getMediaType()}
     */
    public static final int VIDEO = 1 << 1;

    @IntRange(from = INVALID_POSITION)
    protected int currentPosition = INVALID_POSITION;
    @IntRange(from = INVALID_ID)
    protected long playlistId = INVALID_ID;

    @SupportedMediaType
    protected int allowedTypeFlag = AUDIO;
    @NonNull
    protected WeakReference<VideoPlayerApi> videoPlayer = new WeakReference<>(null);

    @Nullable
    protected PlaylistServiceCore<I, ?> service;

    @NonNull
    protected List<WeakReference<PlaylistListener<I>>> playlistListeners = new LinkedList<>();
    @NonNull
    protected List<WeakReference<ProgressListener>> progressListeners = new LinkedList<>();
    @NonNull
    protected ReentrantLock playlistListenersLock = new ReentrantLock(true);
    @NonNull
    protected ReentrantLock progressListenersLock = new ReentrantLock(true);

    @Nullable
    protected Intent seekEndedIntent, allowedTypeChangedIntent;
    @Nullable
    protected PendingIntent updateNotificationPendingIntent, playPausePendingIntent, nextPendingIntent, previousPendingIntent, stopPendingIntent, repeatPendingIntent, shufflePendingIntent, seekStartedPendingIntent;

    /**
     * Retrieves the application to use when starting and communicating with the
     * PlaylistService specified with {@link #getMediaServiceClass()}.
     *
     * @return The Application to use when starting and controlling the PlaylistService
     */
    @NonNull
    protected abstract Application getApplication();

    /**
     * Retrieves the class that represents the PlaylistService.  This is used when communicating
     * with the service to perform the playback controls.
     *
     * @return The class for the Service to control.  This should extend {@link BasePlaylistService}
     */
    @NonNull
    protected abstract Class<? extends Service> getMediaServiceClass();

    /**
     * A basic constructor that will retrieve the application
     * via {@link #getApplication()}.
     */
    public BasePlaylistManager() {
        constructControlIntents(getMediaServiceClass(), getApplication());
    }

    /**
     * A constructor that will use the specified application to initialize
     * the PlaylistManager.  This should only be used in instances that
     * {@link #getApplication()} will not be prepared at this point.  This
     * can happen when using Dependence Injection frameworks such as Dagger.
     *
     * @param application The application to use to initialize the PlaylistManager
     */
    public BasePlaylistManager(@NonNull Application application) {
        constructControlIntents(getMediaServiceClass(), application);
    }

    /**
     * Resets the current positions and ids
     */
    public void reset() {
        setId(INVALID_ID);
        setCurrentPosition(0);
    }

    /**
     * This is a pass through method that is called from the {@link BasePlaylistService} to inform
     * any listeners that are registered through {@link #registerPlaylistListener(PlaylistListener)}
     *
     * @param currentItem The new playback item
     * @param hasNext True if there exists an item after the <code>currentItem</code> in the playlist
     * @param hasPrevious True if there exists an item before the <code>currentItem</code> in the playlist
     * @return True if the event should be consumed
     */
    @Override
    public boolean onPlaylistItemChanged(@Nullable I currentItem, boolean hasNext, boolean hasPrevious) {
        playlistListenersLock.lock();
        Iterator<WeakReference<PlaylistListener<I>>> iterator = playlistListeners.iterator();

        while (iterator.hasNext()) {
            PlaylistListener<I> listener = iterator.next().get();
            if (listener == null) {
                iterator.remove();
                continue;
            }

            if (listener.onPlaylistItemChanged(currentItem, hasNext, hasPrevious)) {
                playlistListenersLock.unlock();
                return true;
            }
        }

        playlistListenersLock.unlock();
        return false;
    }

    /**
     * This is a pass through method that is called from the {@link BasePlaylistService} to inform
     * any listeners that are registered through {@link #registerPlaylistListener(PlaylistListener)}
     *
     * @param playbackState The new media playback state
     * @return True if the event should be consumed
     */
    @Override
    public boolean onPlaybackStateChanged(@NonNull BasePlaylistService.PlaybackState playbackState) {
        playlistListenersLock.lock();
        Iterator<WeakReference<PlaylistListener<I>>> iterator = playlistListeners.iterator();

        while (iterator.hasNext()) {
            PlaylistListener listener = iterator.next().get();
            if (listener == null) {
                iterator.remove();
                continue;
            }

            if (listener.onPlaybackStateChanged(playbackState)) {
                playlistListenersLock.unlock();
                return true;
            }
        }

        playlistListenersLock.unlock();
        return false;
    }

    /**
     * This is a pass through method that is called from the {@link BasePlaylistService} to inform
     * any listeners that are registered through {@link #registerPlaylistListener(PlaylistListener)}
     *
     * @param mediaProgress The current media progress
     * @return True if the mediaProgress should be consumed
     */
    @Override
    public boolean onProgressUpdated(@NonNull MediaProgress mediaProgress) {
        progressListenersLock.lock();
        Iterator<WeakReference<ProgressListener>> iterator = progressListeners.iterator();

        while (iterator.hasNext()) {
            ProgressListener listener = iterator.next().get();
            if (listener == null) {
                iterator.remove();
                continue;
            }

            if (listener.onProgressUpdated(mediaProgress)) {
                progressListenersLock.unlock();
                return true;
            }
        }

        progressListenersLock.unlock();
        return false;
    }

    /**
     * Retrieves the most recent media playback state.
     *
     * @return The most recent PlaybackState
     */
    @NonNull
    public BasePlaylistService.PlaybackState getCurrentPlaybackState() {
        if (service != null) {
            return service.getCurrentPlaybackState();
        }

        return BasePlaylistService.PlaybackState.STOPPED;
    }

    /**
     * Retrieves the current progress for the media playback
     *
     * @return The most recent progress event
     */
    @Nullable
    public MediaProgress getCurrentProgress() {
        return service != null ? service.getCurrentMediaProgress() : null;
    }

    /**
     * Retrieves the most recent {@link PlaylistItemChange}
     *
     * @return The most recent Item Changed information
     */
    @Nullable
    public PlaylistItemChange<I> getCurrentItemChange() {
        return service != null ? service.getCurrentItemChange() : null;
    }

    /**
     * Links the {@link BasePlaylistService} so that we can correctly manage the
     * {@link PlaylistListener}
     *
     * @param service The AudioService to link to this manager
     */
    public void registerService(@NonNull PlaylistServiceCore<I, ?> service) {
        this.service = service;
    }

    /**
     * UnLinks the {@link BasePlaylistService} from this manager. (see {@link #registerService(PlaylistServiceCore)}
     */
    public void unRegisterService() {
        service = null;
    }

    /**
     * Registers the listener to this service.  These callbacks will only be
     * called if {@link #registerService(PlaylistServiceCore)} has been called.
     *
     * @param listener The listener to register
     */
    public void registerPlaylistListener(@NonNull PlaylistListener<I> listener) {
        playlistListenersLock.lock();
        playlistListeners.add(new WeakReference<>(listener));
        playlistListenersLock.unlock();
    }

    /**
     * UnRegisters the specified listener.  This should be called when the listener
     * class losses focus, or should be destroyed.
     *
     * @param listener The listener to remove
     */
    public void unRegisterPlaylistListener(@NonNull PlaylistListener listener) {
        playlistListenersLock.lock();
        Iterator<WeakReference<PlaylistListener<I>>> iterator = playlistListeners.iterator();

        while (iterator.hasNext()) {
            PlaylistListener playlistListener = iterator.next().get();
            if (playlistListener == null || playlistListener.equals(listener)) {
                iterator.remove();
            }
        }

        playlistListenersLock.unlock();
    }

    /**
     * Registers the listener to be notified of progress updates.
     *
     * @param listener The listener to notify of progress updates
     */
    public void registerProgressListener(@NonNull ProgressListener listener) {
        progressListenersLock.lock();
        progressListeners.add(new WeakReference<>(listener));
        progressListenersLock.unlock();
    }

    /**
     * UnRegisters the specified listener.  This should only be called for listeners
     * that have been registered with {@link #registerProgressListener(ProgressListener)}
     *
     * @param listener The listener to unregister
     */
    public void unRegisterProgressListener(@NonNull ProgressListener listener) {
        progressListenersLock.lock();
        Iterator<WeakReference<ProgressListener>> iterator = progressListeners.iterator();

        while (iterator.hasNext()) {
            ProgressListener progressListener = iterator.next().get();
            if (progressListener == null || progressListener.equals(listener)) {
                iterator.remove();
            }
        }

        progressListenersLock.unlock();
    }

    /**
     * Performs the functionality to play the current item in the playlist.  This will
     * interact with the service specified with {@link #getMediaServiceClass()}.  If there
     * are no items in the current playlist then no action will be performed.
     *
     * @param seekPosition The position to start the current items playback at (milliseconds)
     * @param startPaused True if the media item should not start playing when it has been prepared
     */
    public void play(@IntRange(from = 0) long seekPosition, boolean startPaused) {
        I currentItem = getCurrentItem();
        if (currentItem == null) {
            return;
        }

        //Starts the playlist service
        Intent intent = new Intent(getApplication(), getMediaServiceClass());
        intent.setAction(RemoteActions.ACTION_START_SERVICE);
        intent.putExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, seekPosition);
        intent.putExtra(RemoteActions.ACTION_EXTRA_START_PAUSED, startPaused);
        getApplication().startService(intent);
    }

    /**
     * Sets the ID associated with the current playlist
     *
     * @param id The id for the playlist, or {@link #INVALID_ID}
     */
    public void setId(@IntRange(from = INVALID_ID) long id) {
        this.playlistId = id;
    }

    /**
     * Sets the type of media that we can currently play.  When set,
     * the {@link #next()} and {@link #previous()} will skip any items
     * that do not match the allowed type.  This should be one or a combination of
     * {@link #AUDIO}, {@link #VIDEO}, or any custom types supported by the extending
     * class.
     *
     * @param flags The flags depicting the allowed media types
     */
    public void setAllowedMediaType(@SupportedMediaType int flags) {
        this.allowedTypeFlag = flags;

        //Tries to start the intent
        if (allowedTypeChangedIntent != null) {
            allowedTypeChangedIntent.putExtra(RemoteActions.ACTION_EXTRA_ALLOWED_TYPE, flags);
            getApplication().startService(allowedTypeChangedIntent);
        }
    }

    /**
     * Sets the current playback position.  This should only be used when jumping
     * down the current playback list, if you are only changing one see {@link #next()} or
     * {@link #previous()}.
     *
     * @param position The position to become the current playback position.
     */
    public void setCurrentPosition(@IntRange(from = 0) int position) {
        if (position >= getItemCount()) {
            position = getItemCount() - 1;
        }

        currentPosition = findNextAllowedPosition(position);
    }

    /**
     * Retrieves the current item position
     *
     * @return The current items position or {@link #INVALID_POSITION}
     */
    @IntRange(from = INVALID_POSITION)
    public int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * Attempts to find the position for the item with the specified itemId.  If no
     * such item exists then the current position will NOT be modified.  However if the item
     * is found then that position will be used to update the current position.  You can also
     * manually set the current position with {@link #setCurrentPosition(int)}.
     *
     * @param itemId The items id to use for finding the new position
     */
    public void setCurrentItem(@IntRange(from = 0) long itemId) {
        int position = getPositionForItem(itemId);
        if (position != INVALID_POSITION) {
            setCurrentPosition(position);
        }
    }

    /**
     * Determines the position for the item with the passed id.
     *
     * @param itemId The items id to use for finding the position
     * @return The items position or {@link #INVALID_POSITION}
     */
    @IntRange(from = INVALID_POSITION)
    public abstract int getPositionForItem(@IntRange(from = 0) long itemId);

    /**
     * Determines if the given ItemQuery is the same as the current item
     *
     * @param item The ItemQuery to compare to the current item
     * @return True if the current item matches the passed item
     */
    public boolean isPlayingItem(@Nullable I item) {
        I currentItem = getCurrentItem();

        //noinspection SimplifiableIfStatement
        if (item == null || currentItem == null) {
            return false;
        }

        return item.getId() == currentItem.getId() && item.getPlaylistId() == playlistId;
    }

    /**
     * Determines if there is another item in the play list after the current one.
     *
     * @return True if there is an item after the current one
     */
    public boolean isNextAvailable() {
        return getItemCount() > findNextAllowedPosition(currentPosition + 1);
    }

    /**
     * Determines if there is an item in the play list before the current one.
     *
     * @return True if there is an item before the current one
     */
    public boolean isPreviousAvailable() {
        return findPreviousAllowedPosition(currentPosition - 1) != getItemCount();
    }

    /**
     * Returns the current playlistId for this playlist.
     *
     * @return The playlist id [default: {@link #INVALID_ID}]
     */
    @IntRange(from = INVALID_ID)
    public long getId() {
        return playlistId;
    }

    /**
     * Determines the current items type.  This will be one of
     * {@link #AUDIO}, {@link #VIDEO}, or any custom types provided by
     * the extending class.  If the current item doesn't exist then
     * 0 will be returned
     *
     * @return The flag associated with the current media type or 0
     */
    @SupportedMediaType
    public int getCurrentItemType() {
        I item = getCurrentItem();
        return item != null ? item.getMediaType() : 0;
    }

    /**
     * Returns the current size of the playlist.
     *
     * @return The size of the playlist
     */
    @IntRange(from = 0)
    public abstract int getItemCount();

    /**
     * Retrieves the item at the given position in the playlist.  If the playlist
     * is null or the position is out of bounds then null will be returned.
     *
     * @param position The position in the playlist to grab the item for
     * @return The retrieved item or null
     */
    @Nullable
    public abstract I getItem(@IntRange(from = 0) int position);

    /**
     * Retrieves the Item representing the currently selected
     * item.  If there aren't any items in the play list then null will
     * be returned instead.
     *
     * @return The current Item or null
     */
    @Nullable
    public I getCurrentItem() {
        if (currentPosition != INVALID_POSITION && currentPosition < getItemCount()) {
            return getItem(currentPosition);
        }

        return null;
    }

    /**
     * Updates the currently selected item to the next one and retrieves the
     * Item representing that item.  If there aren't any items in the play
     * list or there isn't a next item then null will be returned.
     *
     * @return The next Item or null
     */
    @Nullable
    public I next() {
        currentPosition = findNextAllowedPosition(currentPosition + 1);
        return getCurrentItem();
    }

    /**
     * Updates the currently selected item to the previous one and retrieves the
     * Item representing that item.  If there aren't any items in the play
     * list or there isn't a previous item then null will be returned.
     *
     * @return The previous Item or null
     */
    @Nullable
    public I previous() {
        currentPosition = findPreviousAllowedPosition(currentPosition - 1);
        return getCurrentItem();
    }

    /**
     * Holds a weak reference to the LDSVideoView to use for playback events such as next or previous.
     *
     * @param videoPlayer The LDSVideoView to use, or null
     */
    public void setVideoPlayer(@Nullable VideoPlayerApi videoPlayer) {
        this.videoPlayer = new WeakReference<>(videoPlayer);
    }

    /**
     * Retrieves the video player specified with {@link #setVideoPlayer(VideoPlayerApi)}
     *
     * @return The {@link VideoPlayerApi} or null
     */
    @Nullable
    public VideoPlayerApi getVideoPlayer() {
        return videoPlayer.get();
    }

    /**
     * Informs the Media service that the current item
     * needs to be played/paused.  The service specified with
     * {@link #getMediaServiceClass()}} will be informed using the action
     * {@link RemoteActions#ACTION_PLAY_PAUSE}
     */
    public void invokePausePlay() {
        sendPendingIntent(playPausePendingIntent);
    }

    /**
     * Informs the Media service that we need to seek to
     * the next item. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_NEXT}
     */
    public void invokeNext() {
        sendPendingIntent(nextPendingIntent);
    }

    /**
     * Informs the Media service that we need to seek to
     * the previous item. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_PREVIOUS}
     */
    public void invokePrevious() {
        sendPendingIntent(previousPendingIntent);
    }

    /**
     * Informs the Media service that we need to stop
     * playback. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_STOP}
     */
    public void invokeStop() {
        sendPendingIntent(stopPendingIntent);
    }

    /**
     * Informs the Media service that we need to repeat
     * the current playback item. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_REPEAT}
     */
    public void invokeRepeat() {
        sendPendingIntent(repeatPendingIntent);
    }

    /**
     * Informs the Media service that we need to shuffle the
     * current playlist items. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_SHUFFLE}
     */
    public void invokeShuffle() {
        sendPendingIntent(shufflePendingIntent);
    }

    /**
     * Informs the Media service that we have started seeking
     * the playback.  The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_SEEK_STARTED}
     */
    public void invokeSeekStarted() {
        sendPendingIntent(seekStartedPendingIntent);
    }

    /**
     * Informs the Media service that we need to seek
     * the current item. The service specified with
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_SEEK_ENDED} and have an intent extra with the
     * key {@link RemoteActions#ACTION_EXTRA_SEEK_POSITION} (long)
     */
    public void invokeSeekEnded(@IntRange(from = 0) long seekPosition) {
        //Tries to start the intent
        if (seekEndedIntent != null) {
            seekEndedIntent.putExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, seekPosition);
            getApplication().startService(seekEndedIntent);
        }
    }

    /**
     * Update status bar notification if user change some data
     * {@link #getMediaServiceClass()} will be informed using the action
     * {@link RemoteActions#ACTION_SEEK_ENDED} and have an intent extra with the
     * key {@link RemoteActions#ACTION_UPDATE_NOTIFICATION} (integer)
     */
    public void updateNotification() {
        sendPendingIntent( updateNotificationPendingIntent );
    }

    /**
     * Creates the Intents that will be used to interact with the playlist service
     *
     * @param mediaServiceClass The class to inform of any media playback controls
     * @param application The application to use when constructing the intents used to inform the playlist service of invocations
     */
    protected void constructControlIntents(@NonNull Class<? extends Service> mediaServiceClass, @NonNull Application application) {
        previousPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_PREVIOUS);
        nextPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_NEXT);
        playPausePendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_PLAY_PAUSE);
        repeatPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_REPEAT);
        shufflePendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_SHUFFLE);

        updateNotificationPendingIntent = createPendingIntent( application, mediaServiceClass, RemoteActions.ACTION_UPDATE_NOTIFICATION );

        stopPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_STOP);
        seekStartedPendingIntent = createPendingIntent(application, mediaServiceClass, RemoteActions.ACTION_SEEK_STARTED);

        seekEndedIntent = new Intent(application, mediaServiceClass);
        seekEndedIntent.setAction(RemoteActions.ACTION_SEEK_ENDED);

        allowedTypeChangedIntent = new Intent(application, mediaServiceClass);
        allowedTypeChangedIntent.setAction(RemoteActions.ACTION_ALLOWED_TYPE_CHANGED);
    }

    /**
     * Finds the next item position that has an allowed type
     *
     * @param position The position to start with
     * @return The new position, or the list size if none exist
     */
    @IntRange(from = 0)
    protected int findNextAllowedPosition(@IntRange(from = 0) int position) {
        if (position >= getItemCount()) {
            return getItemCount();
        }

        while (position < getItemCount() && position >= 0 && !isAllowedType(getItem(position))) {
            position++;
        }

        return position < getItemCount() ? position : getItemCount();
    }

    /**
     * Finds the previous item position that has an allowed type
     *
     * @param position The position to start with
     * @return The new position, or the list size if none exist
     */
    @IntRange(from = 0)
    protected int findPreviousAllowedPosition(@IntRange(from = 0) int position) {
        if (position >= getItemCount()) {
            return getItemCount();
        }

        while (position >= 0 && !isAllowedType(getItem(position))) {
            position--;
        }

        return position >= 0 ? position : getItemCount();
    }

    /**
     * Determines if the passed item is of the correct type to allow playback
     *
     * @param item The item to determine if it is allowed
     * @return True if the item is null or is allowed
     */
    protected boolean isAllowedType(@Nullable I item) {
        //noinspection SimplifiableIfStatement
        if (item == null || item.getMediaType() == 0) {
            return false;
        }

        return (allowedTypeFlag & item.getMediaType()) != 0;
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param application The application to use when creating the  pending intent
     * @param serviceClass The service class to notify of intents
     * @param action The action to use
     * @return The resulting PendingIntent
     */
    @NonNull
    protected PendingIntent createPendingIntent(@NonNull Application application, @NonNull Class<? extends Service> serviceClass, @NonNull String action) {
        Intent intent = new Intent(application, serviceClass);
        intent.setAction(action);

        return PendingIntent.getService(application, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Attempts to send the specified PendingIntent.
     *
     * @param pi The pending intent to send
     */
    protected void sendPendingIntent(@Nullable PendingIntent pi) {
        if (pi == null) {
            return;
        }

        try {
            pi.send();
        } catch (Exception e) {
            Log.d(TAG, "Error sending pending intent " + pi.toString(), e);
        }
    }
}
