package com.devbrackets.android.playlistcore.manager;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import java.util.List;

/**
 * An implementation of the {@link PlaylistManager} that supports Lists
 */
public abstract class ListPlaylistManager<I extends IPlaylistItem> extends PlaylistManager<I> {
    @Nullable
    protected List<I> items;

    /**
     * A utility method to allow for single line implementations to start playing the media
     * item as specified by the passed parameters.
     *
     * @param items The list of items to play
     * @param startIndex The index in the playlistItems to start playback
     * @param seekPosition The position in the startIndex item to start at (in milliseconds)
     * @param startPaused True if the media item should start paused instead of playing
     */
    public void play(@Nullable List<I> items, @IntRange(from = 0) int startIndex, @IntRange(from = 0) int seekPosition, boolean startPaused) {
        setParameters(items, startIndex);
        play(seekPosition, startPaused);
    }

    /**
     * Sets the List of items to be used for the play list.  This can include both audio
     * and video items.
     *
     * @param items The List of items to play
     * @param startIndex The index in the list to start playback with
     */
    public void setParameters(@Nullable List<I> items, @IntRange(from = 0) int startIndex) {
        this.items = items;

        setCurrentIndex(startIndex);
        setId(INVALID_PLAYLIST_ID);
    }

    @Override
    public int getSize() {
        return items != null ? items.size() : 0;
    }

    @Override
    @Nullable
    public I getItem(@IntRange(from = 0) int index) {
        if (items != null && index < getSize()) {
            return items.get(index);
        }

        return null;
    }

    @Override
    public int getIndexForItem(long itemId) {
        if (items == null) {
            return INVALID_PLAYLIST_INDEX;
        }

        int index = 0;
        for (I item : items) {
            if (item.getId() == itemId) {
                return index;
            }

            index++;
        }

        return INVALID_PLAYLIST_INDEX;
    }
}