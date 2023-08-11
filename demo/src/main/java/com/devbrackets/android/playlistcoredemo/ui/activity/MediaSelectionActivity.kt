package com.devbrackets.android.playlistcoredemo.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.devbrackets.android.playlistcore.annotation.SupportedMediaType
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.data.Samples
import com.devbrackets.android.playlistcoredemo.ui.activity.AudioPlayerActivity
import com.devbrackets.android.playlistcoredemo.ui.activity.MediaSelectionActivity
import com.devbrackets.android.playlistcoredemo.ui.activity.VideoPlayerActivity
import com.devbrackets.android.playlistcoredemo.ui.adapter.SampleListAdapter

/**
 * A simple activity that allows the user to select a chapter form "The Count of Monte Cristo"
 * or a sample video to play.
 */
class MediaSelectionActivity : AppCompatActivity(), OnItemClickListener {
  private val isAudio by lazy {
    intent.getLongExtra(EXTRA_MEDIA_TYPE, BasePlaylistManager.AUDIO.toLong()) == BasePlaylistManager.AUDIO.toLong()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.list_selection_activity)

    supportActionBar?.title = resources.getString(if (isAudio) R.string.title_audio_selection_activity else R.string.title_video_selection_activity)

    setupList()
  }

  private fun setupList() {
    val exampleList = findViewById<ListView>(R.id.selection_activity_list)
    exampleList.adapter = SampleListAdapter(this, if (isAudio) Samples.audio else Samples.video)
    exampleList.onItemClickListener = this
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
    if (isAudio) {
      startActivity(AudioPlayerActivity.intent(this, position))
    } else {
      startActivity(VideoPlayerActivity.intent(this, position))
    }
  }

  companion object {
    const val EXTRA_MEDIA_TYPE = "EXTRA_MEDIA_TYPE"
    fun show(activity: Activity, @SupportedMediaType mediaType: Long) {
      val intent = Intent(activity, MediaSelectionActivity::class.java)
      intent.putExtra(EXTRA_MEDIA_TYPE, mediaType)
      activity.startActivity(intent)
    }
  }
}