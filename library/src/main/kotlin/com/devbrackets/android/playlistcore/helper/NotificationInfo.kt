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

package com.devbrackets.android.playlistcore.helper

import android.app.PendingIntent
import android.graphics.Bitmap
import android.support.annotation.DrawableRes

/**
 * An object to hold the information necessary to populate a notification
 */
class NotificationInfo {
    var title: String = ""
    var album: String = ""
    var artist: String = ""

    var largeImage: Bitmap? = null
    var secondaryImage: Bitmap? = null

    @DrawableRes
    @get:DrawableRes
    var appIcon: Int = 0
    var notificationId: Int = 0

    var showNotifications: Boolean = false

    var pendingIntent: PendingIntent? = null

    var mediaState: NotificationHelper.NotificationMediaState? = null

    fun clean() {
        appIcon = 0
        notificationId = 0

        title = ""
        album = ""
        artist = ""

        largeImage = null
        secondaryImage = null
        pendingIntent = null
    }
}
