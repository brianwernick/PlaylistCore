package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.data.Samples;
import com.devbrackets.android.playlistcoredemo.ui.adapter.SampleListAdapter;


/**
 * A simple activity that allows the user to select a
 * video to play
 */
public class VideoSelectionActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_selection_activity);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getResources().getString(R.string.title_video_selection_activity));
        }

        ListView exampleList = (ListView) findViewById(R.id.selection_activity_list);
        exampleList.setAdapter(new SampleListAdapter(this, Samples.getVideoSamples()));
        exampleList.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        startVideoPlayerActivity(position);
    }

    private void startVideoPlayerActivity(int selectedIndex) {
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra(VideoPlayerActivity.EXTRA_INDEX, selectedIndex);
        startActivity(intent);
    }
}