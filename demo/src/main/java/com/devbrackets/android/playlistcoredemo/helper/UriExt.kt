package com.devbrackets.android.playlistcoredemo.helper

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.devbrackets.android.exomedia.util.getExtension

private val extensionToMimeMap = mapOf(
  ".mp3" to MimeTypes.AUDIO_MPEG,
  ".mp4" to MimeTypes.VIDEO_MP4,
  ".m3u8" to MimeTypes.APPLICATION_M3U8,
  ".mpd" to "application/dash+xml",
  ".ism" to "application/vnd.ms-sstr+xml",
)

/**
 * Determines the Content-Type (previously Mime-Type) for the Uri based on the extension
 *
 * @return The Content-Type of the [Uri] or `null` if it is unknown or unsupported
 */
internal fun Uri.getContentType(): String? {
  val extension = getExtension().trim { it <= ' ' }
  return if (extension.isNotBlank()) {
    extensionToMimeMap[extension]
  } else {
    null
  }
}
