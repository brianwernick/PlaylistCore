package com.devbrackets.android.playlistcoredemo

import android.app.Application
import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager

class App : Application() {
  val playlistManager: PlaylistManager by lazy {
    PlaylistManager(this)
  }

  override fun onCreate() {
    super.onCreate()
    application = this
  }

  override fun onTerminate() {
    super.onTerminate()
    application = null
  }

  companion object {
    var application: App? = null
      private set
  }
}