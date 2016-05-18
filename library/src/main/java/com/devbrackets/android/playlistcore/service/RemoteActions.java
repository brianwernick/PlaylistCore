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

package com.devbrackets.android.playlistcore.service;

/**
 * A simple container for the remote actions used by the
 * {@link com.devbrackets.android.playlistcore.manager.BasePlaylistManager}
 * to inform the {@link BasePlaylistService} of processes to handle.
 */
public class RemoteActions {
    private static final String PREFIX = "com.devbrackets.android.playlistcore.";

    public static final String ACTION_START_SERVICE = PREFIX + "start_service";

    public static final String ACTION_PLAY_PAUSE = PREFIX + "play_pause";
    public static final String ACTION_PREVIOUS = PREFIX + "previous";
    public static final String ACTION_NEXT = PREFIX + "next";
    public static final String ACTION_STOP = PREFIX + "stop";
    public static final String ACTION_REPEAT = PREFIX + "repeat";
    public static final String ACTION_SHUFFLE = PREFIX + "shuffle";

    public static final String ACTION_UPDATE_NOTIFICATION = PREFIX + "update_notification";

    public static final String ACTION_SEEK_STARTED = PREFIX + "seek_started";
    public static final String ACTION_SEEK_ENDED = PREFIX + "seek_ended";

    public static final String ACTION_ALLOWED_TYPE_CHANGED = PREFIX + "allowed_type_changed";

    //Extras
    public static final String ACTION_EXTRA_SEEK_POSITION = PREFIX + "seek_position";
    public static final String ACTION_EXTRA_ALLOWED_TYPE = PREFIX + "allowed_type";
    public static final String ACTION_EXTRA_START_PAUSED = PREFIX + "start_paused";
}
