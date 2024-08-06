package dev.langchain4j.data.message;

public class AudioContent {
    private String audioUrl;
    private int duration;

    public AudioContent(String audioUrl, int duration) {
        this.audioUrl = audioUrl;
        this.duration = duration;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}