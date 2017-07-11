package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.app.Activity;
import android.os.Bundle;
import android.widget.VideoView;

import com.devbrackets.android.playlistcoredemo.App;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.MediaItem;
import com.devbrackets.android.playlistcoredemo.data.Samples;
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;

import java.util.LinkedList;
import java.util.List;


public class VideoPlayerActivity extends Activity {
    public static final String EXTRA_INDEX = "EXTRA_INDEX";
    public static final int PLAYLIST_ID = 6; //Arbitrary, for the example (different from audio)

    protected VideoView videoView;
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
    protected void onStop() {
        super.onStop();
        playlistManager.invokeStop();
    }

    /**
     * Retrieves the extra associated with the selected playlist index
     * so that we can start playing the correct item.
     */
    protected void retrieveExtras() {
        Bundle extras = getIntent().getExtras();
        selectedIndex = extras.getInt(EXTRA_INDEX, 0);
    }

    protected void init() {
        setupPlaylistManager();

        videoView = (VideoView) findViewById(R.id.video_play_activity_video_view);

//        playlistManager.setVideoPlayer(new VideoApi(videoView)); //todo add to the mediaPlayers list in the service... should we have a Manager function to do this?
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
