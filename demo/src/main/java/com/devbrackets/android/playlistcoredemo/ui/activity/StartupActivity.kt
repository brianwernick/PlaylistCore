package com.devbrackets.android.playlistcoredemo.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.ui.adapter.StartupListAdapter

class StartupActivity : AppCompatActivity(), OnItemClickListener {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.startup_activity)
    val exampleList = findViewById<ListView>(R.id.startup_activity_list)
    exampleList.adapter = StartupListAdapter(this)
    exampleList.onItemClickListener = this
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
    when (position) {
      StartupListAdapter.INDEX_AUDIO_PLAYBACK -> MediaSelectionActivity.show(this, BasePlaylistManager.AUDIO.toLong())
      StartupListAdapter.INDEX_VIDEO_PLAYBACK -> MediaSelectionActivity.show(this, BasePlaylistManager.VIDEO.toLong())
      else -> {}
    }
  }
}