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

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.devbrackets.android.playlistcore.annotation.ServiceContinuationMethod;
import com.devbrackets.android.playlistcore.annotation.SupportedMediaType;
import com.devbrackets.android.playlistcore.api.AudioPlayerApi;
import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.api.VideoPlayerApi;
import com.devbrackets.android.playlistcore.event.MediaProgress;
import com.devbrackets.android.playlistcore.event.PlaylistItemChange;
import com.devbrackets.android.playlistcore.helper.AudioFocusHelper;
import com.devbrackets.android.playlistcore.listener.OnMediaCompletionListener;
import com.devbrackets.android.playlistcore.listener.OnMediaErrorListener;
import com.devbrackets.android.playlistcore.listener.OnMediaPreparedListener;
import com.devbrackets.android.playlistcore.listener.OnMediaSeekCompletionListener;
import com.devbrackets.android.playlistcore.listener.PlaylistListener;
import com.devbrackets.android.playlistcore.listener.ProgressListener;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcore.manager.IPlaylistItem;
import com.devbrackets.android.playlistcore.util.MediaProgressPoll;

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
public abstract class PlaylistServiceCore<I extends IPlaylistItem, M extends BasePlaylistManager<I>> extends Service implements AudioFocusHelper.AudioFocusCallback, ProgressListener {
    private static final String TAG = "PlaylistServiceCore";

    public enum PlaybackState {
        RETRIEVING,    // the MediaRetriever is retrieving music
        PREPARING,     // Preparing / Buffering
        PLAYING,       // Active but could be paused due to loss of audio focus Needed for returning after we regain focus
        PAUSED,        // Paused but player ready
        SEEKING,       // performSeek was called, awaiting seek completion callback
        STOPPED,       // Stopped not preparing media
        ERROR          // An error occurred, we are stopped
    }

    @Nullable //Null if the WAKE_LOCK permission wasn't requested
    protected WifiManager.WifiLock wifiLock;
    protected AudioFocusHelper audioFocusHelper;

    protected AudioPlayerApi audioPlayer;
    @NonNull
    protected MediaProgressPoll mediaProgressPoll = new MediaProgressPoll();
    @NonNull
    protected AudioListener audioListener = new AudioListener();
    @NonNull
    protected MediaProgress currentMediaProgress = new MediaProgress(0, 0, 0);

    @NonNull
    protected PlaybackState currentState = PlaybackState.PREPARING;

    @Nullable
    protected I currentPlaylistItem;
    protected int seekToPosition = -1;

    protected boolean pausedForSeek = false;
    protected boolean immediatelyPause = false;
    protected boolean pausedForFocusLoss = false;
    protected boolean onCreateCalled = false;

    @Nullable
    protected Intent workaroundIntent = null;

    /**
     * Retrieves a new instance of the {@link AudioPlayerApi}. This will only
     * be called the first time an Audio Player is needed; afterwards a cached
     * instance will be used.
     *
     * @return The {@link AudioPlayerApi} to use for playback
     */
    @NonNull
    protected abstract AudioPlayerApi getNewAudioPlayer();

    protected abstract void setupAsForeground();
    protected abstract void setupForeground();
    protected abstract void stopForeground();

    /**
     * Retrieves the volume level to use when audio focus has been temporarily
     * lost due to a higher importance notification.  The media will continue
     * playback but be reduced to the specified volume for the duration of the
     * notification.  This usually happens when receiving an alert for MMS, EMail,
     * etc.
     *
     * @return The volume level to use when a temporary notification sounds. [0.0 - 1.0]
     */
    @FloatRange(from = 0.0, to = 1.0)
    protected abstract float getAudioDuckVolume();

    /**
     * Links the {@link BasePlaylistManager} that contains the information for playback
     * to this service.
     *
     * NOTE: this is only used for retrieving information, it isn't used to register notifications
     * for playlist changes, however as long as the change isn't breaking (e.g. cleared playlist)
     * then nothing additional needs to be performed.
     *
     * @return The {@link BasePlaylistManager} containing the playback information
     */
    @NonNull
    protected abstract M getPlaylistManager();

    /**
     * Retrieves the continuity bits associated with the service.  These
     * are the bits returned by {@link #onStartCommand(Intent, int, int)} and can be
     * one of the {@link #START_CONTINUATION_MASK} values.
     *
     * @return The continuity bits for the service [default: {@link #START_NOT_STICKY}]
     */
    @ServiceContinuationMethod
    public int getServiceContinuationMethod() {
        return START_NOT_STICKY;
    }

    /**
     * Used to determine if the device is connected to a network that has
     * internet access.  This is used in conjunction with {@link #isDownloaded(IPlaylistItem)}
     * to determine what items in the playlist manager, specified with {@link #getPlaylistManager()}, can be
     * played.
     *
     * @return True if the device currently has internet connectivity
     */
    protected boolean isNetworkAvailable() {
        return true;
    }

    /**
     * Used to determine if the specified playlistItem has been downloaded.  If this is true
     * then the downloaded copy will be used instead, and no network wakelock will be acquired.
     *
     * @param playlistItem The playlist item to determine if it is downloaded.
     * @return True if the specified playlistItem is downloaded. [default: false]
     */
    protected boolean isDownloaded(I playlistItem) {
        return false;
    }

    /**
     * Called when the media player has failed to play the current audio item.
     */
    protected void onMediaPlayerResetting() {
        //Purposefully left blank
    }

    /**
     * Called when the {@link #performStop()} has been called.
     *
     * @param playlistItem The playlist item that has been stopped
     */
    protected void onMediaStopped(I playlistItem) {
        //Purposefully left blank
    }

    /**
     * Called when a current audio item has ended playback.  This is called when we
     * are unable to play an audio item.
     *
     * @param playlistItem The PlaylistItem that has ended
     * @param currentPosition The position the playlist item ended at
     * @param duration The duration of the PlaylistItem
     */
    protected void onAudioPlaybackEnded(I playlistItem, long currentPosition, long duration) {
        //Purposefully left blank
    }

    /**
     * Called when an audio item has started playback.
     *
     * @param playlistItem The PlaylistItem that has started playback
     * @param currentPosition The position the playback has started at
     * @param duration The duration of the PlaylistItem
     */
    protected void onAudioPlaybackStarted(I playlistItem, long currentPosition, long duration) {
        //Purposefully left blank
    }

    /**
     * Called when the service is unable to seek to the next playable item when
     * no network is available.
     */
    protected void onNoNonNetworkItemsAvailable() {
        //Purposefully left blank
    }

    /**
     * Called when an audio item in playback has ended
     */
    protected void onAudioPlaybackEnded() {
        //Purposefully left blank
    }

    /**
     * Called when the notification needs to be updated.
     * This occurs when playback state updates or the current
     * item in playback is changed.
     */
    protected void updateNotification() {
        //Purposefully left blank
    }

    /**
     * Similar to {@link #updateNotification()}, this is called when
     * the remote views need to be updated due to playback state
     * updates and item changes.
     *
     * The remote views handle the lock screen, bluetooth controls,
     * Android Wear interactions, etc.
     */
    protected void updateRemoteViews() {
        //Purposefully left blank
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        //Part of a workaround for some Samsung devices (see onStartCommand)
        if (onCreateCalled) {
            return;
        }

        onCreateCalled = true;
        super.onCreate();
        Log.d(TAG, "Service Created");

        onServiceCreate();
    }

    /**
     * Stops the current media in playback and releases all
     * held resources.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "Service Destroyed");
        setPlaybackState(PlaybackState.STOPPED);

        relaxResources(true);
        getPlaylistManager().unRegisterService();
        audioFocusHelper.setAudioFocusCallback(null);
        audioFocusHelper.abandonFocus();

        audioFocusHelper = null;

        onCreateCalled = false;
    }

    /**
     * Handles the intents posted by the {@link BasePlaylistManager} through
     * the <code>invoke*</code> methods.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return getServiceContinuationMethod();
        }

        //This is a workaround for an issue on the Samsung Galaxy S3 (4.4.2) where the onStartCommand will occasionally get called before onCreate
        if (!onCreateCalled) {
            Log.d(TAG, "Starting Samsung workaround");
            workaroundIntent = intent;
            onCreate();
            return getServiceContinuationMethod();
        }

        if (RemoteActions.ACTION_START_SERVICE.equals(intent.getAction())) {
            startItemPlayback();

            seekToPosition = intent.getIntExtra(RemoteActions.ACTION_EXTRA_SEEK_POSITION, -1);
            immediatelyPause = intent.getBooleanExtra(RemoteActions.ACTION_EXTRA_START_PAUSED, false);
        } else {
            handleRemoteAction(intent.getAction(), intent.getExtras());
        }

        return getServiceContinuationMethod();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        onDestroy();
    }

    /**
     * Updates the playback state and volume based on the
     * previous state held before {@link #onAudioFocusLost(boolean)}
     * was called.
     */
    @Override
    public boolean onAudioFocusGained() {
        if (!currentItemIsType(BasePlaylistManager.AUDIO)) {
            return false;
        }

        //Returns the audio to the previous playback state and volume
        if (!audioPlayer.isPlaying() && pausedForFocusLoss) {
            audioPlayer.play();
            updateNotification();
        } else {
            audioPlayer.setVolume(1.0f, 1.0f); //reset the audio volume
        }

        return true;
    }

    /**
     * Audio focus is lost either temporarily or permanently due
     * to external changes such as notification sounds and
     * phone calls.  When focus is lost temporarily, the
     * audio volume will decrease for the duration of the focus
     * loss (returned to normal in {@link #onAudioFocusGained()}.
     * If the focus is lost permanently then the audio will pause
     * playback and attempt to resume when {@link #onAudioFocusGained()}
     * is called.
     */
    @Override
    public boolean onAudioFocusLost(boolean canDuckAudio) {
        if (!currentItemIsType(BasePlaylistManager.AUDIO)) {
            return false;
        }

        //Either pauses or reduces the volume of the audio in playback
        if (audioFocusHelper.getCurrentAudioFocus() == AudioFocusHelper.Focus.NO_FOCUS_NO_DUCK) {
            if (audioPlayer.isPlaying()) {
                pausedForFocusLoss = true;
                audioPlayer.pause();
                updateNotification();
            }
        } else {
            audioPlayer.setVolume(getAudioDuckVolume(), getAudioDuckVolume());
        }

        return true;
    }

    /**
     * When the current media progress is updated we call through the
     * {@link BasePlaylistManager} to inform any listeners of the change
     */
    @Override
    public boolean onProgressUpdated(@NonNull MediaProgress mediaProgress) {
        currentMediaProgress = mediaProgress;
        return getPlaylistManager().onProgressUpdated(mediaProgress);
    }

    /**
     * Retrieves the current playback state of the service.
     *
     * @return The current playback state
     */
    public PlaybackState getCurrentPlaybackState() {
        return currentState;
    }

    /**
     * Retrieves the current playback progress.
     *
     * @return The current playback progress
     */
    @NonNull
    public MediaProgress getCurrentMediaProgress() {
        return currentMediaProgress;
    }

    /**
     * Retrieves the current item change event which represents any media item changes.
     * This is intended as a utility method for initializing, or returning to, a media
     * playback UI.  In order to get the changed events you will need to register for
     * callbacks through {@link BasePlaylistManager#registerPlaylistListener(PlaylistListener)}
     *
     * @return The current PlaylistItem Changed event
     */
    public PlaylistItemChange<I> getCurrentItemChange() {
        boolean hasNext = getPlaylistManager().isNextAvailable();
        boolean hasPrevious = getPlaylistManager().isPreviousAvailable();

        return new PlaylistItemChange<>(currentPlaylistItem, hasPrevious, hasNext);
    }

    /**
     * Used to perform the onCreate functionality when the service is actually created.  This
     * should be overridden instead of {@link #onCreate()} due to a bug in some Samsung devices
     * where {@link #onStartCommand(Intent, int, int)} will get called before {@link #onCreate()}.
     */
    protected void onServiceCreate() {
        mediaProgressPoll.setProgressListener(this);
        audioFocusHelper = new AudioFocusHelper(getApplicationContext());
        audioFocusHelper.setAudioFocusCallback(this);

        //Attempts to obtain the wifi lock only if the manifest has requested the permission
        if (getPackageManager().checkPermission(Manifest.permission.WAKE_LOCK, getPackageName()) == PackageManager.PERMISSION_GRANTED) {
            wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "mcLock");
            wifiLock.setReferenceCounted(false);
        } else {
            Log.w(TAG, "Unable to acquire WAKE_LOCK due to missing manifest permission");
        }

        getPlaylistManager().registerService(this);

        //Another part of the workaround for some Samsung devices
        if (workaroundIntent != null) {
            startService(workaroundIntent);
            workaroundIntent = null;
        }
    }

    /**
     * A generic method to determine if media is currently playing.  This is
     * used to determine the playback state for the notification.
     *
     * @return True if media is currently playing
     */
    protected boolean isPlaying() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            return audioPlayer != null && audioPlayer.isPlaying();
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            return getPlaylistManager().getVideoPlayer() != null && getPlaylistManager().getVideoPlayer().isPlaying();
        }

        return false;
    }

    /**
     * Performs the functionality to pause and/or resume
     * the media playback.  This is called through an intent
     * with the {@link RemoteActions#ACTION_PLAY_PAUSE}, through
     * {@link BasePlaylistManager#invokePausePlay()}
     */
    protected void performPlayPause() {
        if (isPlaying() || pausedForFocusLoss) {
            pausedForFocusLoss = false;
            performPause();
        } else {
            performPlay();
        }

        updateRemoteViews();
        updateNotification();
    }

    /**
     * Performs the functionality to seek to the previous media
     * item.  This is called through an intent
     * with the {@link RemoteActions#ACTION_PREVIOUS}, through
     * {@link BasePlaylistManager#invokePrevious()}
     */
    protected void performPrevious() {
        seekToPosition = 0;
        immediatelyPause = !isPlaying();

        getPlaylistManager().previous();
        startItemPlayback();
    }

    /**
     * Performs the functionality to seek to the next media
     * item.  This is called through an intent
     * with the {@link RemoteActions#ACTION_NEXT}, through
     * {@link BasePlaylistManager#invokeNext()}
     */
    protected void performNext() {
        seekToPosition = 0;
        immediatelyPause = !isPlaying();

        getPlaylistManager().next();
        startItemPlayback();
    }

    /**
     * Performs the functionality to repeat the current
     * media item in playback.  This is called through an
     * intent with the {@link RemoteActions#ACTION_REPEAT},
     * through {@link BasePlaylistManager#invokeRepeat()}
     */
    protected void performRepeat() {
        //Left for the extending class to implement
    }

    /**
     * Performs the functionality to repeat the current
     * media item in playback.  This is called through an
     * intent with the {@link RemoteActions#ACTION_SHUFFLE},
     * through {@link BasePlaylistManager#invokeShuffle()}
     */
    protected void performShuffle() {
        //Left for the extending class to implement
    }

    /**
     * Performs the functionality for when a media item
     * has finished playback.  By default the completion
     * will seek to the next available media item.  This is
     * called from the Audio listener.
     */
    protected void performMediaCompletion() {
        //Left for the extending class to implement
    }

    /**
     * Performs the functionality to start a seek for the current
     * media item.  This is called through an intent
     * with the {@link RemoteActions#ACTION_SEEK_STARTED}, through
     * {@link BasePlaylistManager#invokeSeekStarted()}
     */
    protected void performSeekStarted() {
        VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
        boolean isPlaying = (currentItemIsType(BasePlaylistManager.AUDIO) && audioPlayer.isPlaying()) ||
                (currentItemIsType(BasePlaylistManager.VIDEO) && videoPlayer != null && videoPlayer.isPlaying());

        if (isPlaying) {
            pausedForSeek = true;
            performPause();
        }
    }

    /**
     * Performs the functionality to end a seek for the current
     * media item.  This is called through an intent
     * with the {@link RemoteActions#ACTION_SEEK_ENDED}, through
     * {@link BasePlaylistManager#invokeSeekEnded(int)}
     */
    protected void performSeekEnded(int newPosition) {
        performSeek(newPosition);
    }

    /**
     * Sets the media type to be allowed in the playlist.  If the type is changed
     * during media playback, the current item will be compared against the new
     * allowed type.  If the current item type and the new type are not compatible
     * then the playback will be seeked to the next valid item.
     *
     * @param newType The new allowed media type
     */
    protected void updateAllowedMediaType(@SupportedMediaType int newType) {
        //We seek through the items until an allowed one is reached, or none is reached and the service is stopped.
        if (currentPlaylistItem != null && (newType & currentPlaylistItem.getMediaType()) == 0) {
            performNext();
        }
    }

    /**
     * Informs the callbacks specified with {@link BasePlaylistManager#registerPlaylistListener(PlaylistListener)}
     * that the current playlist item has changed.
     */
    protected void postPlaylistItemChanged() {
        boolean hasNext = getPlaylistManager().isNextAvailable();
        boolean hasPrevious = getPlaylistManager().isPreviousAvailable();
        getPlaylistManager().onPlaylistItemChanged(currentPlaylistItem, hasNext, hasPrevious);
    }

    /**
     * Informs the callbacks specified with {@link BasePlaylistManager#registerPlaylistListener(PlaylistListener)}
     * that the current media state has changed.
     */
    protected void postPlaybackStateChanged() {
        getPlaylistManager().onPlaybackStateChanged(currentState);
    }

    /**
     * Performs the functionality to stop the media playback.  This will perform any cleanup
     * and stop the service.
     */
    protected void performStop() {
        setPlaybackState(PlaybackState.STOPPED);
        if (currentPlaylistItem != null) {
            onMediaStopped(currentPlaylistItem);
        }

        // let go of all resources
        relaxResources(true);
        audioFocusHelper.abandonFocus();

        getPlaylistManager().reset();
        stopSelf();
    }

    /**
     * Performs the functionality to seek the current media item
     * to the specified position.  This should only be called directly
     * when performing the initial setup of playback position.  For
     * normal seeking process use the {@link #performSeekStarted()} in
     * conjunction with {@link #performSeekEnded(int)}
     *
     * @param position The position to seek to in milliseconds
     */
    protected void performSeek(int position) {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            if (audioPlayer != null) {
                audioPlayer.seekTo(position);
            }
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
            if (videoPlayer != null) {
                videoPlayer.seekTo(position);
            }
        }

        setPlaybackState(PlaybackState.SEEKING);
    }

    /**
     * Performs the functionality to actually pause the current media
     * playback.
     */
    protected void performPause() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            if (audioPlayer != null) {
                audioPlayer.pause();
            }
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
            if (videoPlayer != null) {
                videoPlayer.pause();
            }
        }

        setPlaybackState(PlaybackState.PAUSED);
        stopForeground();
    }

    /**
     * Performs the functionality to actually play the current media
     * item.
     */
    protected void performPlay() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            if (audioPlayer != null) {
                audioPlayer.play();
            }
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
            if (videoPlayer != null) {
                videoPlayer.play();
            }
        }

        setPlaybackState(PlaybackState.PLAYING);
        setupForeground();
    }

    /**
     * Determines if the current media item is of the passed type.  This is specified
     * with {@link IPlaylistItem#getMediaType()}
     *
     * @return True if the current media item is of the same passed type
     */
    protected boolean currentItemIsType(@SupportedMediaType int type) {
        return currentPlaylistItem != null && (currentPlaylistItem.getMediaType() & type) != 0;
    }

    /**
     * Starts the actual item playback, correctly determining if the
     * item is a video or an audio item.
     * <p>
     * <em><b>NOTE:</b></em> In order to play videos you will need to specify the
     * VideoView with {@link BasePlaylistManager#setVideoPlayer(VideoPlayerApi)}
     */
    protected void startItemPlayback() {
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            onAudioPlaybackEnded();
        }

        seekToNextPlayableItem();
        mediaItemChanged();

        //Performs the playback for the correct media type
        boolean playbackHandled = false;
        if (currentItemIsType(BasePlaylistManager.AUDIO)) {
            audioListener.resetRetryCount();
            playbackHandled = playAudioItem();
        } else if (currentItemIsType(BasePlaylistManager.VIDEO)) {
            playbackHandled = playVideoItem();
        } else if (currentPlaylistItem != null && currentPlaylistItem.getMediaType() != 0) {
            playbackHandled = playOtherItem();
        }

        if (playbackHandled) {
            return;
        }

        //If the playback wasn't handled, attempt to seek to the next playable item, otherwise stop the service
        if (getPlaylistManager().isNextAvailable()) {
            performNext();
        } else {
            performStop();
        }
    }

    /**
     * Starts the actual playback of the specified audio item.
     *
     * @return True if the item playback was correctly handled
     */
    protected boolean playAudioItem() {
        stopVideoPlayback();
        initializeAudioPlayer();
        audioFocusHelper.requestFocus();

        boolean isItemDownloaded = isDownloaded(currentPlaylistItem);
        audioPlayer.setStreamType(AudioManager.STREAM_MUSIC);

        //noinspection ConstantConditions -  currentPlaylistItem is not null at this point (see calling method for null check)
        audioPlayer.setDataSource(this, Uri.parse(isItemDownloaded ? currentPlaylistItem.getDownloadedMediaUri() : currentPlaylistItem.getMediaUrl()));

        setPlaybackState(PlaybackState.PREPARING);
        setupAsForeground();

        audioPlayer.prepareAsync();

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        updateWiFiLock(!isItemDownloaded);
        return true;
    }

    /**
     * Starts the actual playback of the specified video item.
     *
     * @return True if the item playback was correctly handled
     * TODO: we should be treating Video playback with the same level of control as audio (playback states, foreground, etc.)
     */
    protected boolean playVideoItem() {
        stopAudioPlayback();
        setupAsForeground();

        VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
        if (videoPlayer == null) {
            return false;
        }

        videoPlayer.stop();
        boolean isItemDownloaded = isDownloaded(currentPlaylistItem);

        //noinspection ConstantConditions -  currentPlaylistItem is not null at this point (see calling method for null check)
        videoPlayer.setDataSource(Uri.parse(isItemDownloaded ? currentPlaylistItem.getDownloadedMediaUri() : currentPlaylistItem.getMediaUrl()));

        // If we are streaming from the internet, we want to hold a Wifi lock, which prevents
        // the Wifi radio from going to sleep while the song is playing. If, on the other hand,
        // we are NOT streaming, we want to release the lock.
        updateWiFiLock(!isItemDownloaded);
        return true;
    }

    /**
     * Starts the playback of the specified other item type.
     *
     * @return True if the item playback was correctly handled
     */
    protected boolean playOtherItem() {
        return false;
    }

    /**
     * Stops the AudioPlayer from playing.
     */
    protected void stopAudioPlayback() {
        audioFocusHelper.abandonFocus();

        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer.reset();
        }
    }

    /**
     * Stops the VideoView from playing if we have access to it.
     */
    protected void stopVideoPlayback() {
        VideoPlayerApi videoPlayer = getPlaylistManager().getVideoPlayer();
        if (videoPlayer != null) {
            videoPlayer.stop();
            videoPlayer.reset();
        }
    }

    /**
     * Reconfigures audioPlayer according to audio focus settings and starts/restarts it. This
     * method starts/restarts the audioPlayer respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * audioPlayer paused or set it to a low volume, depending on what is allowed by the
     * current focus settings.
     */
    protected void startAudioPlayer() {
        if (audioPlayer == null) {
            return;
        }

        if (audioFocusHelper.getCurrentAudioFocus() == AudioFocusHelper.Focus.NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck we have to pause, even if state is playing
            // Be we stay in the playing state so we know we have to resume playback once we get the focus back.
            if (audioPlayer.isPlaying()) {
                audioPlayer.pause();
                onAudioPlaybackEnded(currentPlaylistItem, audioPlayer.getCurrentPosition(), audioPlayer.getDuration());
            }

            return;
        } else if (audioFocusHelper.getCurrentAudioFocus() == AudioFocusHelper.Focus.NO_FOCUS_CAN_DUCK) {
            audioPlayer.setVolume(getAudioDuckVolume(), getAudioDuckVolume());
        } else {
            audioPlayer.setVolume(1.0f, 1.0f);
        }

        mediaProgressPoll.start();
        if (!audioPlayer.isPlaying()) {
            audioPlayer.play();
            onAudioPlaybackStarted(currentPlaylistItem, audioPlayer.getCurrentPosition(), audioPlayer.getDuration());
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the "foreground service"
     * status and notification, the wake locks, and the audioPlayer if requested
     *
     * @param releaseAudioPlayer True if the audioPlayer should be released
     */
    protected void relaxResources(boolean releaseAudioPlayer) {
        mediaProgressPoll.release();

        if (releaseAudioPlayer) {
            if (audioPlayer != null) {
                audioPlayer.reset();
                audioPlayer.release();
                audioPlayer = null;
            }

            getPlaylistManager().setCurrentPosition(Integer.MAX_VALUE);
        }

        updateWiFiLock(false);
    }

    /**
     * Acquires or releases the WiFi lock
     *
     * @param acquire True if the WiFi lock should be acquired, false to release
     */
    protected void updateWiFiLock(boolean acquire) {
        if (wifiLock == null) {
            return;
        }

        if (acquire && !wifiLock.isHeld()) {
            wifiLock.acquire();
        } else if (!acquire && wifiLock.isHeld()) {
            wifiLock.release();
        }
    }

    /**
     * Updates the current PlaybackState and informs any listening classes.
     *
     * @param state The new PlaybackState
     */
    protected void setPlaybackState(PlaybackState state) {
        currentState = state;
        postPlaybackStateChanged();
    }

    /**
     * Iterates through the playList, starting with the current item, until we reach an item we can play.
     * Normally this will be the current item, however if they don't have network then
     * it will be the next downloaded item.
     */
    protected void seekToNextPlayableItem() {
        I currentItem = getPlaylistManager().getCurrentItem();
        if (currentItem == null) {
            currentPlaylistItem = null;
            return;
        }

        //Only iterate through the list if we aren't connected to the internet
        if (!isNetworkAvailable()) {
            while (currentItem != null && !isDownloaded(currentItem)) {
                currentItem = getPlaylistManager().next();
            }
        }

        //If we are unable to get a next playable item, post a network error
        if (currentItem == null) {
            onNoNonNetworkItemsAvailable();
        }

        currentPlaylistItem = getPlaylistManager().getCurrentItem();
    }

    /**
     * Called when the current media item has changed, this will update the notification and
     * lock screen values.
     */
    protected void mediaItemChanged() {
        //Validates that the currentPlaylistItem is for the currentItem
        if (!getPlaylistManager().isPlayingItem(currentPlaylistItem)) {
            Log.d(TAG, "forcing currentPlaylistItem update");
            currentPlaylistItem = getPlaylistManager().getCurrentItem();
        }

        postPlaylistItemChanged();
    }

    /**
     * Handles the remote actions from the big notification and lock screen to control
     * the audio playback
     *
     * @param action The action from the intent to handle
     * @param extras The extras packaged with the intent associated with the action
     */
    protected void handleRemoteAction(String action, Bundle extras) {
        if (action == null || action.isEmpty()) {
            return;
        }

        switch (action) {
            case RemoteActions.ACTION_PLAY_PAUSE:
                performPlayPause();
                break;

            case RemoteActions.ACTION_NEXT:
                performNext();
                break;

            case RemoteActions.ACTION_PREVIOUS:
                performPrevious();
                break;

            case RemoteActions.ACTION_REPEAT:
                performRepeat();
                break;

            case RemoteActions.ACTION_SHUFFLE:
                performShuffle();
                break;

            case RemoteActions.ACTION_STOP:
                performStop();
                break;

            case RemoteActions.ACTION_SEEK_STARTED:
                performSeekStarted();
                break;

            case RemoteActions.ACTION_SEEK_ENDED:
                performSeekEnded(extras.getInt(RemoteActions.ACTION_EXTRA_SEEK_POSITION, 0));
                break;

            case RemoteActions.ACTION_ALLOWED_TYPE_CHANGED:
                //noinspection WrongConstant
                updateAllowedMediaType(extras.getInt(RemoteActions.ACTION_EXTRA_ALLOWED_TYPE));
                break;

            default:
                break;
        }
    }

    /**
     * Initializes the audio player.
     * If the audio player has already been initialized, then it will
     * be reset to prepare for the next playback item.
     */
    protected void initializeAudioPlayer() {
        if (audioPlayer != null) {
            audioPlayer.reset();
            return;
        }

        audioPlayer = getNewAudioPlayer();
        mediaProgressPoll.update(audioPlayer);
        mediaProgressPoll.reset();
        audioPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        //Sets the listeners
        audioPlayer.setOnMediaPreparedListener(audioListener);
        audioPlayer.setOnMediaCompletionListener(audioListener);
        audioPlayer.setOnMediaErrorListener(audioListener);
        audioPlayer.setOnMediaSeekCompletionListener(audioListener);
    }

    /**
     * A class to listen to the EMAudioPlayer events, and will
     * retry audio playback once when an error is encountered.
     * This is done to workaround an issue on older (pre 4.1)
     * devices where playback will fail due to a race condition
     * in the {@link MediaPlayer}
     *
     * TODO: this only handles audio.... what about videos?
     */
    protected class AudioListener implements OnMediaPreparedListener, OnMediaCompletionListener, OnMediaErrorListener, OnMediaSeekCompletionListener {
        private static final int MAX_RETRY_COUNT = 1;
        private int retryCount = 0;

        @Override
        public void onPrepared(@NonNull MediaPlayerApi mediaPlayerApi) {
            //Make sure to only perform this functionality when playing audio
            if (!currentItemIsType(BasePlaylistManager.AUDIO)) {
                return;
            }

            retryCount = 0;
            setPlaybackState(PlaybackState.PLAYING);
            startAudioPlayer();

            //Immediately pauses
            if (immediatelyPause) {
                immediatelyPause = false;
                if (audioPlayer.isPlaying()) {
                    performPause();
                }
            }

            //Seek to the correct position
            if (seekToPosition > 0) {
                performSeek(seekToPosition);
                seekToPosition = -1;
            }

            updateRemoteViews();
            updateNotification();
        }

        @Override
        public void onCompletion(@NonNull MediaPlayerApi mediaPlayerApi) {
            //Make sure to only perform this functionality when playing audio
            if (currentItemIsType(BasePlaylistManager.AUDIO)) {
                performMediaCompletion();
            }
        }

        @Override
        public boolean onError(@NonNull MediaPlayerApi mediaPlayerApi) {
            //Make sure to only perform this functionality when playing audio
            if (!currentItemIsType(BasePlaylistManager.VIDEO)) {
                return false;
            }

            //The retry count is a workaround for when the EMAudioPlayer will occasionally fail to load valid content due to the MediaPlayer on pre 4.1 devices
            if (++retryCount <= MAX_RETRY_COUNT) {
                Log.d(TAG, "Retrying audio playback.  Retry count: " + retryCount);
                playAudioItem();
                return false;
            }

            onMediaPlayerResetting();

            setPlaybackState(PlaybackState.ERROR);
            relaxResources(true);
            audioFocusHelper.abandonFocus();
            return false;
        }

        @Override
        public void onSeekComplete(@NonNull MediaPlayerApi mediaPlayerApi) {
            if (pausedForSeek) {
                performPlay();
                pausedForSeek = false;
            } else {
                performPause();
            }
        }

        public void resetRetryCount() {
            retryCount = 0;
        }
    }
}