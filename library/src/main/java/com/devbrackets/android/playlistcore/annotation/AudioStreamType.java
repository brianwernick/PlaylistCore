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

package com.devbrackets.android.playlistcore.annotation;

import android.media.AudioManager;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
//        AudioManager.STREAM_BLUETOOTH_SCO, // 5
//        AudioManager.STREAM_SYSTEM_ENFORCED, // 6
        AudioManager.STREAM_DTMF,
//        AudioManager.STREAM_TTS // 9
})
@Retention(RetentionPolicy.SOURCE)
public @interface AudioStreamType {
    //Purposefully left blank
}
