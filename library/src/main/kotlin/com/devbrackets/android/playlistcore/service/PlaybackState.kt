package com.devbrackets.android.playlistcore.service

enum class PlaybackState {
    RETRIEVING, // the MediaRetriever is retrieving music
    PREPARING, // Preparing / Buffering
    PLAYING, // Active but could be paused due to loss of audio focus Needed for returning after we regain focus
    PAUSED, // Paused but player ready
    SEEKING, // performSeek was called, awaiting seek completion callback
    STOPPED, // Stopped not preparing media
    ERROR          // An error occurred, we are stopped
}