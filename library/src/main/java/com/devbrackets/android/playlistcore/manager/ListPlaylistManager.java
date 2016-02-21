/*
 * Copyright (C) 2016 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.manager;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;

import java.util.List;

/**
 * An implementation of the {@link BasePlaylistManager} that supports Lists
 *
 * {@inheritDoc}
 */
@SuppressWarnings("unused")
public abstract class ListPlaylistManager<I extends IPlaylistItem> extends BasePlaylistManager<I> {
    @Nullable
    protected List<I> items;

    /**
     * A utility method to allow for single line implementations to start playing the media
     * item as specified by the passed parameters.
     *
     * @param items The list of items to play
     * @param startPosition The position in the playlistItems to start playback
     * @param playbackPosition The playback position in the item located at <code>startPosition</code> to start at in milliseconds
     * @param startPaused True if the media item should start paused instead of playing
     */
    public void play(@Nullable List<I> items, @IntRange(from = 0) int startPosition, @IntRange(from = 0) int playbackPosition, boolean startPaused) {
        setParameters(items, startPosition);
        play(playbackPosition, startPaused);
    }

    /**
     * Sets the List of items to be used for the play list.  This can include both audio
     * and video items.
     *
     * @param items The List of items to play
     * @param startPosition The position in the list to start playback with
     */
    public void setParameters(@Nullable List<I> items, @IntRange(from = 0) int startPosition) {
        this.items = items;

        setCurrentPosition(startPosition);
        setId(INVALID_ID);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    @Override
    @Nullable
    public I getItem(@IntRange(from = 0) int position) {
        if (items != null && position < getItemCount()) {
            return items.get(position);
        }

        return null;
    }

    @Override
    public int getPositionForItem(long itemId) {
        if (items == null) {
            return INVALID_POSITION;
        }

        int itemCount = getItemCount();
        for (int position = 0; position < itemCount; position++) {
            I item = getItem(position);
            if (item != null && item.getId() == itemId) {
                return position;
            }
        }

        return INVALID_POSITION;
    }
}