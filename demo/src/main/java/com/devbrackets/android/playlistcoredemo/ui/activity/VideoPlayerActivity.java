package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.app.Activity;
import android.os.Bundle;

import com.devbrackets.android.exomedia.listener.VideoControlsSeekListener;
import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.devbrackets.android.playlistcore.data.PlaybackState;
import com.devbrackets.android.playlistcore.listener.PlaylistListener;
import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.data.Samples;
import com.devbrackets.android.playlistcoredemo.helper.VideoApi;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;


public class VideoPlayerActivity extends Activity implements VideoControlsSeekListener, PlaylistListener<MediaItem> {
    public static final String EXTRA_INDEX = "EXTRA_INDEX";
    public static final int PLAYLIST_ID = 6; //Arbitrary, for the example (different from audio)

    protected VideoView videoView;
    protected VideoApi videoApi;
    protected PlaylistManager playlistManager;

    protected int selectedIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_player_activity);

        retrieveExtras();
        init();
    }

    @Override
    protected void onStart() {
        super.onStart();
        playlistManager.registerPlaylistListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        playlistManager.unRegisterPlaylistListener(this);
        playlistManager.removeVideoApi(videoApi);
        playlistManager.invokeStop();
    }

    @Override
    public boolean onSeekStarted() {
        playlistManager.invokeSeekStarted();
        return true;
    }

    @Override
    public boolean onSeekEnded(long seekTime) {
        playlistManager.invokeSeekEnded(seekTime);
        return true;
    }

    @Override
    public boolean onPlaylistItemChanged(@Nullable MediaItem currentItem, boolean hasNext, boolean hasPrevious) {
        // Purposefully left blank
        return false;
    }

    @Override
    public boolean onPlaybackStateChanged(@NotNull PlaybackState playbackState) {
        if (playbackState == PlaybackState.STOPPED) {
            finish();
            return true;
        }

        return false;
    }

    /**
     * Retrieves the extra associated with the selected playlist index
     * so that we can start playing the correct item.
     */
    protected void retrieveExtras() {
        Bundle extras = getIntent().getExtras();
        selectedIndex = extras != null ? extras.getInt(EXTRA_INDEX, 0) : 0;
    }

    protected void init() {
        setupPlaylistManager();

        videoView = findViewById(R.id.video_play_activity_video_view);
        videoView.setHandleAudioFocus(false);
        videoView.getVideoControls().setSeekListener(this);

        videoApi = new VideoApi(videoView);
        playlistManager.addVideoApi(videoApi);
        playlistManager.play(0, false);
    }

    /**
     * Retrieves the playlist instance and performs any generation
     * of content if it hasn't already been performed.
     */
    private void setupPlaylistManager() {
        playlistManager = App.getPlaylistManager();

        List<MediaItem> mediaItems = new LinkedList<>();
        for (Samples.Sample sample : Samples.getVideoSamples()) {
            MediaItem mediaItem = new MediaItem(sample, false);
            mediaItems.add(mediaItem);
        }

        playlistManager.setParameters(mediaItems, selectedIndex);
        playlistManager.setId(PLAYLIST_ID);
    }
}
