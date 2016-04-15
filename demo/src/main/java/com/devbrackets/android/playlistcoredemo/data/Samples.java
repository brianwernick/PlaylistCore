package com.devbrackets.android.playlistcoredemo.data;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Samples {
    @NonNull
    private static final List<Sample> audioSamples;
    @NonNull
    private static final List<Sample> videoSamples;

    static {
        String audioImage = "https://ia902708.us.archive.org/3/items/count_monte_cristo_0711_librivox/Count_Monte_Cristo_1110.jpg?cnt=0";

        /* AUDIO
         * These items are licensed under CreativeCommons from LibriVox
         * Additional files can be found at https://archive.org/details/count_monte_cristo_0711_librivox
         */
        audioSamples = new LinkedList<>();
        audioSamples.add(new Sample("Marseilles -- The Arrival", "https://archive.org/download/count_monte_cristo_0711_librivox/count_of_monte_cristo_001_dumas.mp3", audioImage));
        audioSamples.add(new Sample("Father and Son", "https://archive.org/download/count_monte_cristo_0711_librivox/count_of_monte_cristo_002_dumas.mp3", audioImage));
        audioSamples.add(new Sample("The Catalans", "https://archive.org/download/count_monte_cristo_0711_librivox/count_of_monte_cristo_003_dumas.mp3", audioImage));
        audioSamples.add(new Sample("Conspiracy", "https://archive.org/download/count_monte_cristo_0711_librivox/count_of_monte_cristo_004_dumas.mp3", audioImage));


        /* VIDEO
         * These items are licensed under multiple Public Domain and Open licenses
         */
        videoSamples = new ArrayList<>();
        videoSamples.add(new Sample("Big Buck Bunny", "http://www.sample-videos.com/video/mp4/480/big_buck_bunny_480p_10mb.mp4"));
        videoSamples.add(new Sample("Sintel", "https://ia700401.us.archive.org/13/items/Sintel/sintel-2048-surround_512kb.mp4"));
        videoSamples.add(new Sample("Popeye for President", "https://archive.org/download/Popeye_forPresident/Popeye_forPresident_512kb.mp4"));
        videoSamples.add(new Sample("Elephants Dream", "https://ia700406.us.archive.org/31/items/ElephantsDream/ed_1024_512kb.mp4"));
    }

    @NonNull
    public static List<Sample> getAudioSamples() {
        return audioSamples;
    }

    @NonNull
    public static List<Sample> getVideoSamples() {
        return videoSamples;
    }

    /**
     * A container for the information associated with a
     * sample media item.
     */
    public static class Sample {
        @NonNull
        private String title;
        @NonNull
        private String mediaUrl;
        @Nullable
        private String artworkUrl;

        public Sample(@NonNull String title, @NonNull String mediaUrl) {
            this(title, mediaUrl, null);
        }

        public Sample(@NonNull String title, @NonNull String mediaUrl, @Nullable String artworkUrl) {
            this.title = title;
            this.mediaUrl = mediaUrl;
            this.artworkUrl = artworkUrl;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getMediaUrl() {
            return mediaUrl;
        }

        @Nullable
        public String getArtworkUrl() {
            return artworkUrl;
        }
    }
}
