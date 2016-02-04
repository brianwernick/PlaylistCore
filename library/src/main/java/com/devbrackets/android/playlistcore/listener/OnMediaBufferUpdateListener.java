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

package com.devbrackets.android.playlistcore.listener;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.devbrackets.android.playlistcore.api.MediaPlayerApi;
import com.devbrackets.android.playlistcore.event.MediaProgress;

/**
 * Interface definition of a callback to be invoked indicating buffering
 * status of a media resource being streamed.
 */
public interface OnMediaBufferUpdateListener {
    /**
     * Called to update status in buffering a media stream.
     * The received buffering percentage
     * indicates how much of the content has been buffered or played.
     * For example a buffering update of 80 percent when half the content
     * has already been played indicates that the next 30 percent of the
     * content to play has been buffered.
     *
     * @param mediaPlayerApi The MediaPlayerApi the update pertains to
     * @param percent The integer percent that is buffered [0, {@value MediaProgress#MAX_BUFFER_PERCENT}] inclusive
     */
    void onBufferingUpdate(@NonNull MediaPlayerApi mediaPlayerApi, @IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT) int percent);
}
