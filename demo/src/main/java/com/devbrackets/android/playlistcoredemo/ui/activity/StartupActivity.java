package com.devbrackets.android.playlistcoredemo.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.devbrackets.android.playlistcore.manager.BasePlaylistManager;
import com.devbrackets.android.playlistcoredemo.R;
import com.devbrackets.android.playlistcoredemo.ui.adapter.StartupListAdapter;


public class StartupActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_activity);

        ListView exampleList = findViewById(R.id.startup_activity_list);
        exampleList.setAdapter(new StartupListAdapter(this));
        exampleList.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case StartupListAdapter.INDEX_AUDIO_PLAYBACK:
                MediaSelectionActivity.show(this, BasePlaylistManager.AUDIO);
                break;

            case StartupListAdapter.INDEX_VIDEO_PLAYBACK:
                MediaSelectionActivity.show(this, BasePlaylistManager.VIDEO);
                break;

            default:
        }
    }
}
