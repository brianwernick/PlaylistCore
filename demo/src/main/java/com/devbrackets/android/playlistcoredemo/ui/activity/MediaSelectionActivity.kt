package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.devbrackets.android.playlistcore.annotation.SupportedMediaType;
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.Samples;
import com.devbrackets.android.playlistcoredemo.ui.adapter.SampleListAdapter;


/**
 * A simple activity that allows the user to select a chapter form "The Count of Monte Cristo"
 * or a sample video to play.
 */
public class MediaSelectionActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    public static final String EXTRA_MEDIA_TYPE = "EXTRA_MEDIA_TYPE";

    private boolean isAudio;

    public static void show(@NonNull Activity activity, @SupportedMediaType long mediaType) {
        Intent intent = new Intent(activity, MediaSelectionActivity.class);
        intent.putExtra(EXTRA_MEDIA_TYPE, mediaType);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_selection_activity);
        isAudio = getIntent().getLongExtra(EXTRA_MEDIA_TYPE, BasePlaylistManager.AUDIO) == BasePlaylistManager.AUDIO;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getResources().getString(isAudio ? R.string.title_audio_selection_activity : R.string.title_video_selection_activity));
        }

        ListView exampleList = findViewById(R.id.selection_activity_list);
        exampleList.setAdapter(new SampleListAdapter(this, isAudio ? Samples.getAudioSamples() : Samples.getVideoSamples()));
        exampleList.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (isAudio) {
            startAudioPlayerActivity(position);
        } else {
            startVideoPlayerActivity(position);
        }
    }

    private void startAudioPlayerActivity(int selectedIndex) {
        Intent intent = new Intent(this, AudioPlayerActivity.class);
        intent.putExtra(AudioPlayerActivity.EXTRA_INDEX, selectedIndex);
        startActivity(intent);
    }

    private void startVideoPlayerActivity(int selectedIndex) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_INDEX, selectedIndex);
        startActivity(intent);
    }
}