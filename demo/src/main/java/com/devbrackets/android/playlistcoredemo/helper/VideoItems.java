package com.devbrackets.android.playlistcoredemo.helper;

import java.util.ArrayList;
import java.util.List;

public class VideoItems {
    private final static List<VideoItem> items;

    static {
        items = new ArrayList<>();

        items.add(new VideoItem("Big Buck Bunny", "http://www.sample-videos.com/video/mp4/480/big_buck_bunny_480p_10mb.mp4"));
        items.add(new VideoItem("Sintel", "https://ia700401.us.archive.org/13/items/Sintel/sintel-2048-surround_512kb.mp4"));
        items.add(new VideoItem("Popeye for President", "https://archive.org/download/Popeye_forPresident/Popeye_forPresident_512kb.mp4"));
        items.add(new VideoItem("Elephants Dream", "https://ia700406.us.archive.org/31/items/ElephantsDream/ed_1024_512kb.mp4"));
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
