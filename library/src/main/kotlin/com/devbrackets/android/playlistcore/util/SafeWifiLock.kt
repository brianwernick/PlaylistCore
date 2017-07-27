package com.devbrackets.android.playlistcore.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log

open class SafeWifiLock(context: Context) {
    protected val wifiLock: WifiManager.WifiLock?

    init {
        //Attempts to obtain the wifi lock only if the manifest has requested the permission
        if (context.packageManager.checkPermission(Manifest.permission.WAKE_LOCK, context.packageName) == PackageManager.PERMISSION_GRANTED) {
            wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, "mcLock")
            wifiLock?.setReferenceCounted(false)
        } else {
            Log.e("SafeWifiLock", "Unable to acquire WAKE_LOCK due to missing manifest permission")
            wifiLock = null
        }
    }

    open fun acquire() {
        wifiLock?.apply {
            if (!isHeld) {
                acquire()
            }
        }
    }

    open fun release() {
        wifiLock?.apply {
            if (isHeld) {
                release()
            }
        }
    }

    /**
     * Acquires or releases the WiFi lock
     *
     * @param acquire True if the WiFi lock should be acquired, false to release
     */
    open fun update(acquire: Boolean) {
        if (acquire) {
            acquire()
        } else {
            release()
        }
    }
}