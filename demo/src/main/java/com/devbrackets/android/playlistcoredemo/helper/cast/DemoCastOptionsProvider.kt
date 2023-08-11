package com.devbrackets.android.playlistcoredemo.helper.cast

import android.content.Context
import com.devbrackets.android.playlistcoredemo.R
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Referenced in the AndroidManifest.xml
 */
@Suppress("unused")
class DemoCastOptionsProvider : OptionsProvider {
  companion object {
    private const val DEFAULT_RECEIVER_APP_ID = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
  }

  override fun getCastOptions(context: Context): CastOptions {
    val mediaOptions = CastMediaOptions.Builder().apply {
      setNotificationOptions(null)
    }.build()

    return CastOptions.Builder().apply {
      setReceiverApplicationId(DEFAULT_RECEIVER_APP_ID)
      setCastMediaOptions(mediaOptions)
      setStopReceiverApplicationWhenEndingSession(true)
    }.build()
  }

  override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
    return null
  }
}