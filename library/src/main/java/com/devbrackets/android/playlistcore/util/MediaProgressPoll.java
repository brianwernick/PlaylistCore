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

package com.devbrackets.android.playlistcore.util;

import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.util.Log;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.event.MediaProgress;
import com.devbrackets.android.playlistcore.listener.ProgressListener;

public class MediaProgressPoll {
    private static final String TAG = "MediaProgressPoll";

    protected Repeater pollRepeater = new Repeater();
    protected StopWatch overriddenPositionStopWatch = new StopWatch();
    protected final MediaProgress currentMediaProgressEvent = new MediaProgress(0, 0, 0);

    @Nullable
    protected ProgressListener progressListener;
    @Nullable
    protected MediaPlayerApi mediaPlayerApi;

    protected boolean overridePosition = false;
    protected boolean overrideDuration = false;

    protected long positionOffset = 0;
    protected long overriddenDuration = 0;

    public MediaProgressPoll() {
        pollRepeater.setRepeatListener(new OnRepeat());
    }

    public void setProgressListener(@Nullable ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public void start() {
        if (progressListener == null) {
            Log.w(TAG, "Not started due to no progress listener specified");
            return;
        }

        pollRepeater.start();

        if (overridePosition) {
            overriddenPositionStopWatch.start();
        }
    }

    public void stop() {
        pollRepeater.stop();
        overriddenPositionStopWatch.stop();
    }

    public void reset() {
        stop();
        overriddenPositionStopWatch.reset();

        positionOffset = 0;
        overriddenDuration = 0;
    }

    public void release() {
        reset();
        mediaPlayerApi = null;
        progressListener = null;
    }

    public void update(@Nullable MediaPlayerApi mediaPlayerApi) {
        this.mediaPlayerApi = mediaPlayerApi;
        if (mediaPlayerApi == null) {
            stop();
            return;
        }

        reset();
    }

    /**
     * Sets the delay to use when notifying of progress.  The
     * default is 33 milliseconds, or 30 frames-per-second
     *
     * @param milliSeconds The millisecond delay to use
     */
    public void setProgressPollDelay(int milliSeconds) {
        pollRepeater.setRepeaterDelay(milliSeconds);
    }

    /**
     * Sets the amount of time to change the return value from {@link #getCurrentPosition()}.
     * This value will be reset when a new audio item is selected.
     *
     * @param offset The millisecond value to offset the position
     */
    public void setPositionOffset(int offset) {
        positionOffset = offset;
    }

    /**
     * Restarts the audio position to the start if the position is being overridden (see {@link #setOverridePosition(boolean)}).
     * This will be the value specified with {@link #setPositionOffset(int)} or 0 if it hasn't been set.
     */
    public void restartOverridePosition() {
        overriddenPositionStopWatch.reset();
    }

    /**
     * Sets if the audio position should be overridden, allowing the time to be restarted at will.  This
     * is useful for streaming audio where the audio doesn't have breaks between songs.
     *
     * @param override True if the position should be overridden
     */
    public void setOverridePosition(boolean override) {
        if (override) {
            overriddenPositionStopWatch.start();
        } else {
            overriddenPositionStopWatch.stop();
        }

        overridePosition = override;
    }

    public void setOverrideDuration(boolean override) {
        overrideDuration = override;
    }

    public void setOverriddenDuration(@IntRange(from = 0) long duration) {
        overriddenDuration = duration;
    }

    /**
     * Retrieves the current position of the media item.  If no media item is currently
     * prepared or in playback then the value will be 0.
     *
     * @return The millisecond value for the current position
     */
    @IntRange(from = 0)
    protected long getCurrentPosition() {
        if (overridePosition) {
            return positionOffset + overriddenPositionStopWatch.getTime();
        }

        return mediaPlayerApi != null ? mediaPlayerApi.getCurrentPosition() : 0;
    }

    /**
     * Retrieves the duration of the current media item.  If no media item is currently
     * prepared or in playback then the value will be 0.
     *
     * @return The millisecond value for the duration
     */
    @IntRange(from = 0)
    protected long getDuration() {
        if (overrideDuration) {
            return overriddenDuration;
        }

        return mediaPlayerApi != null ? mediaPlayerApi.getDuration() : 0;
    }

    /**
     * Retrieves the current buffer percent of the media item.  If no media item is currently
     * prepared or in playback then the value will be 0.
     *
     * @return The integer percent that is buffered [0, 100] inclusive
     */
    @IntRange(from = 0, to = 100)
    public int getBufferPercentage() {
        return mediaPlayerApi != null ? mediaPlayerApi.getBufferedPercent() : 0;
    }

    protected class OnRepeat implements Repeater.RepeatListener {
        @Override
        public void onRepeat() {
            currentMediaProgressEvent.update(getCurrentPosition(), getBufferPercentage(), getDuration());
            if (progressListener != null) {
                progressListener.onProgressUpdated(currentMediaProgressEvent);
            } else {
                pollRepeater.stop();
                Log.w(TAG, "Stopping due to no listeners");
            }
        }
    }
}
