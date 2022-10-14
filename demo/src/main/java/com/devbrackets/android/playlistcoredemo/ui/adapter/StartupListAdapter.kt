package com.devbrackets.android.playlistcoredemo.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.devbrackets.android.playlistcoredemo.R

class StartupListAdapter(context: Context) : BaseAdapter() {
  private val examplePages: List<String>
  private val inflater: LayoutInflater by lazy {
    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
  }

  init {
    examplePages = ArrayList()
    examplePages.add(INDEX_AUDIO_PLAYBACK, "Audio Playback")
    examplePages.add(INDEX_VIDEO_PLAYBACK, "Video Playback")
  }

  override fun getCount(): Int {
    return examplePages.size
  }

  override fun getItem(position: Int): Any {
    return examplePages[position]
  }

  override fun getItemId(position: Int): Long {
    return position.toLong()
  }

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view = convertView ?: let {
      inflater.inflate(R.layout.simple_text_item, parent, false).also {
        it.tag = ViewHolder().apply {
          text = it.findViewById(R.id.simple_text_text_view)
        }
      }
    }

    val holder = view.tag as ViewHolder
    holder.text?.text = examplePages[position]

    return view
  }

  private class ViewHolder {
    var text: TextView? = null
  }

  companion object {
    const val INDEX_AUDIO_PLAYBACK = 0
    const val INDEX_VIDEO_PLAYBACK = 1
  }
}