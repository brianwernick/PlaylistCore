package com.devbrackets.android.playlistcoredemo.helper;

import java.util.ArrayList;
import java.util.List;

public class VideoItems {
    private final static List<VideoItem> items;

    static {
        items = new ArrayList<>();

        //TODO: find more video sources
        items.add(new VideoItem("MP4 - Big Buck Bunny by Blender", "http://www.sample-videos.com/video/mp4/480/big_buck_bunny_480p_10mb.mp4"));
    }

    public static List<VideoItem> getItems() {
        return items;
    }

    public static class VideoItem {
        String title;
        String mediaUrl;

        public VideoItem(String title, String mediaUrl) {
            this.title = title;
            this.mediaUrl = mediaUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getMediaUrl() {
            return mediaUrl;
        }
    }
}
