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

import android.database.Cursor;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * An implementation of the {@link BasePlaylistManager} that supports Cursors
 *
 * {@inheritDoc}
 */
@SuppressWarnings("unused")
public abstract class CursorPlaylistManager<I extends IPlaylistItem> extends BasePlaylistManager<I> {
    @Nullable
    protected Cursor cursor;
    protected boolean isValidData;

    protected abstract long getItemId(@NonNull Cursor cursor);

    @Override
    public int getPositionForItem(@IntRange(from = 0) long itemId) {
        if (!isValidData) {
            return INVALID_POSITION;
        }

        int itemCount = getItemCount();
        for (int position = 0; position < itemCount; position++) {
            Cursor positionCursor = getCursor(position);
            if (positionCursor != null && getItemId(positionCursor) == itemId) {
                return position;
            }
        }

        return INVALID_POSITION;
    }

    @Override
    public int getItemCount() {
        if (isValidData && cursor != null) {
            return cursor.getCount();
        }

        return 0;
    }

    /**
     * A utility method to allow for single line implementations to start playing the media
     * item as specified by the passed parameters.
     *
     * @param cursor The cursor that contains the playback items
     * @param startPosition The position in the playlistItems to start playback
     * @param playbackPosition The playback position in the item located at <code>startPosition</code> to start at in milliseconds
     * @param startPaused True if the media item should start paused instead of playing
     */
    public void play(@Nullable Cursor cursor, @IntRange(from = 0) int startPosition, @IntRange(from = 0) int playbackPosition, boolean startPaused) {
        setParameters(cursor, startPosition);
        play(playbackPosition, startPaused);
    }

    /**
     * Sets the List of items to be used for the play list.  This can include both audio
     * and video items.
     *
     * @param cursor The cursor that contains the playback items
     * @param startPosition The position in the list to start playback with
     */
    public void setParameters(@Nullable Cursor cursor, @IntRange(from = 0) int startPosition) {
        changeCursor(cursor);

        setCurrentPosition(startPosition);
        setId(INVALID_ID);
    }

    /**
     * Returns the current cursor
     *
     * @return The current cursor
     */
    @Nullable
    protected Cursor getCursor() {
        return cursor;
    }

    /**
     * Get the cursor associated with the specified position in the data set.
     *
     * @param position The position of the item whose data we want within the adapter's data set.
     * @return The cursor representing the data at the specified position.
     */
    @Nullable
    protected Cursor getCursor(int position) {
        if (isValidData && cursor != null && cursor.moveToPosition(position)) {
            return cursor;
        }

        return null;
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     *
     * @param newCursor The new cursor to be used
     */
    protected void changeCursor(@Nullable Cursor newCursor) {
        Cursor oldCursor = swapCursor(newCursor);
        if (oldCursor != null && !oldCursor.isClosed()) {
            oldCursor.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there was not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    @Nullable
    protected Cursor swapCursor(@Nullable Cursor newCursor) {
        if (newCursor == cursor) {
            return null;
        }

        //Performs the actual cursor swap
        Cursor oldCursor = cursor;
        setupCursor(newCursor);

        return oldCursor;
    }

    /**
     * Updates the global variables, and registers observers to the cursor for any
     * changes in order to notify the implementing class.
     *
     * @param cursor The cursor from which to get the data
     */
    protected void setupCursor(@Nullable Cursor cursor) {
        this.cursor = cursor;
        isValidData = cursor != null;
    }
}