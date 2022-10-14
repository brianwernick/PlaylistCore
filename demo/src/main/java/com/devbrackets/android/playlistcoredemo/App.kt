package com.devbrackets.android.playlistcoredemo;

import android.app.Application;

import com.devbrackets.android.playlistcoredemo.manager.PlaylistManager;

public class App extends Application {

    private static App application;
    private static PlaylistManager playlistManager;

    @Override
    public void onCreate() {
        super.onCreate();

        application = this;
        playlistManager = new PlaylistManager(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        application = null;
        playlistManager = null;
    }

    public static PlaylistManager getPlaylistManager() {
        return playlistManager;
    }

    public static App getApplication() {
        return application;
    }
}
