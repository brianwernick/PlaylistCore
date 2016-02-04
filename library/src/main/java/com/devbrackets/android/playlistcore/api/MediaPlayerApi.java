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

package com.devbrackets.android.playlistcore.api;

import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;

import com.devbrackets.android.playlistcore.event.MediaProgress;
import com.devbrackets.android.playlistcore.listener.OnMediaBufferUpdateListener;
import com.devbrackets.android.playlistcore.listener.OnMediaCompletionListener;
import com.devbrackets.android.playlistcore.listener.OnMediaErrorListener;
import com.devbrackets.android.playlistcore.listener.OnMediaPreparedListener;
import com.devbrackets.android.playlistcore.listener.OnMediaSeekCompletionListener;

public interface MediaPlayerApi {
    /**
     * Determines if media is currently playing on the
     * implementing object
     *
     * @return True if the media is currently playing
     */
    boolean isPlaying();

    void play();

    void pause();

    void stop();

    void reset();

    void release();

    void setVolume(@FloatRange(from = 0.0, to = 1.0) float left, @FloatRange(from = 0.0, to = 1.0) float right);

    void seekTo(@IntRange(from = 0) long milliseconds);

    @IntRange(from = 0)
    long getCurrentPosition();

    @IntRange(from = 0)
    long getDuration();

    /**
     * Retrieves the current buffer percent of the audio item.  If an audio item is not currently
     * prepared or buffering the value will be 0.  This should only be called after the audio item is
     * prepared (see {@link #setOnMediaPreparedListener(OnMediaPreparedListener)})
     *
     * @return The integer percent that is buffered [0, {@value MediaProgress#MAX_BUFFER_PERCENT}] inclusive
     */
    @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT)
    int getBufferedPercent();

    void setOnMediaPreparedListener(OnMediaPreparedListener listener);

    void setOnMediaBufferUpdateListener(OnMediaBufferUpdateListener listener);

    void setOnMediaSeekCompletionListener(OnMediaSeekCompletionListener listener);

    void setOnMediaCompletionListener(OnMediaCompletionListener listener);

    void setOnMediaErrorListener(OnMediaErrorListener listener);
}
