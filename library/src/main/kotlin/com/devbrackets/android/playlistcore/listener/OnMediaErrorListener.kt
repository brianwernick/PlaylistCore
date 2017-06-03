/*
 * Copyright (C) 2016 - 2017 Brian Wernick
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

package com.devbrackets.android.playlistcore.listener

import com.devbrackets.android.playlistcore.api.MediaPlayerApi

/**
 * Interface definition of a callback to be invoked when there
 * has been an error during an asynchronous operation.
 */
interface OnMediaErrorListener {
    /**
     * Called to indicate an error.
     *
     * @param mediaPlayerApi The MediaPlayerApi the error pertains to
     * @return True if the method handled the error, false if it didn't.
     */
    fun onError(mediaPlayerApi: MediaPlayerApi): Boolean
}
