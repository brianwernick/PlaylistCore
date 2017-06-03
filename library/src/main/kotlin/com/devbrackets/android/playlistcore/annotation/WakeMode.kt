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

package com.devbrackets.android.playlistcore.annotation

import android.os.PowerManager
import android.support.annotation.IntDef

@IntDef(
        PowerManager.PARTIAL_WAKE_LOCK.toLong(),
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK.toLong(), //Deprecated in API 13
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK.toLong(), //Deprecated in API 17
        PowerManager.FULL_WAKE_LOCK.toLong() //Deprecated in API 17
)
@Retention(AnnotationRetention.SOURCE)
annotation class WakeMode
