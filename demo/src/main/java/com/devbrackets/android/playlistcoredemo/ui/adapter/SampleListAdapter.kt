package com.devbrackets.android.playlistcoredemo.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.devbrackets.android.playlistcoredemo.R
import com.devbrackets.android.playlistcoredemo.data.Samples

class SampleListAdapter(
  context: Context,
  private val samples: List<Samples.Sample>
) : BaseAdapter() {
  private val inflater: LayoutInflater by lazy {
    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
  }

  override fun getCount(): Int {
    return samples.size
  }

  override fun getItem(position: Int): Any {
    return samples[position]
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
    holder.text?.text = samples[position].title

    return view
  }

  private class ViewHolder {
    var text: TextView? = null
  }
}