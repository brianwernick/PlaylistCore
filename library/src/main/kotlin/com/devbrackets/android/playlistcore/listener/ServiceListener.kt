package com.devbrackets.android.playlistcore.listener

import com.devbrackets.android.playlistcore.manager.PlaylistItem

//TODO: how does Java handle the default implementations?
interface ServiceListener<in I : PlaylistItem> {

    /**
     * Called when the [.performStop] has been called.
     *
     * @param playlistItem The playlist item that has been stopped
     */
    fun onMediaStopped(playlistItem: I) {
        //Purposefully left blank
    }

    /**
     * Called when a current media item has ended playback.  This is called when we
     * are unable to play an item.
     *
     * @param playlistItem The PlaylistItem that has ended
     * @param currentPosition The position the playlist item ended at
     * @param duration The duration of the PlaylistItem
     */
    fun onMediaPlaybackEnded(playlistItem: I, currentPosition: Long, duration: Long) {
        //Purposefully left blank
    }

    /**
     * Called when a media item has started playback.
     *
     * @param playlistItem The PlaylistItem that has started playback
     * @param currentPosition The position the playback has started at
     * @param duration The duration of the PlaylistItem
     */
    fun onMediaPlaybackStarted(playlistItem: I, currentPosition: Long, duration: Long) {
        //Purposefully left blank
    }

    /**
     * Called when the service is unable to seek to the next playable item when
     * no network is available.
     */
    fun onNoNonNetworkItemsAvailable() {
        //Purposefully left blank
    }

    /**
     * Called when a media item in playback has ended
     */
    fun onMediaPlaybackEnded() {
        //Purposefully left blank
    }
}