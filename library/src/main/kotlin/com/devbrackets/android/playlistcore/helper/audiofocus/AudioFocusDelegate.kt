package com.devbrackets.android.playlistcore.helper.audiofocus

open class AudioFocusDelegate<I : com.devbrackets.android.playlistcore.api.PlaylistItem> {

    protected var pausedForFocusLoss = false

    open fun onAudioFocusGained(mediaPlayer: com.devbrackets.android.playlistcore.api.MediaPlayerApi<I>?, currentItem: I?): Boolean {
        mediaPlayer ?: return false
        if (mediaPlayer.handlesOwnAudioFocus) {
            return false
        }

        if (mediaPlayer.isPlaying && pausedForFocusLoss) {
            pausedForFocusLoss = false
            //todo play
        } else {
            //todo resume volume
        }

        return true
    }

    open fun onAudioFocusLost(mediaPlayer: com.devbrackets.android.playlistcore.api.MediaPlayerApi<I>?, currentItem: I?, canDuck: Boolean): Boolean {
        mediaPlayer ?: return false
        if (mediaPlayer.handlesOwnAudioFocus) {
            return false
        }

        if (!canDuck && mediaPlayer.isPlaying) {
            pausedForFocusLoss = true
            // todo pause
        } else if (canDuck) {
            //todo set duck volume
        }

        return true
    }
}