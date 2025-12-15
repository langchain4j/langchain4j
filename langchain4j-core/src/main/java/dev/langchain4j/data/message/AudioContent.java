package dev.langchain4j.data.message;

import dev.langchain4j.data.audio.Audio;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.AUDIO;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class AudioContent implements Content {

    private final Audio audio;

    @Override
    public ContentType type() {
        return AUDIO;
    }

    /**
     * Create a new {@link AudioContent} from the given url.
     *
     * @param url the url of the Audio.
     */
    public AudioContent(URI url) {
        this.audio = Audio.builder()
                .url(ensureNotNull(url, "url"))
                .build();
    }

    /**
     * Create a new {@link AudioContent} from the given url.
     *
     * @param url the url of the Audio.
     */
    public AudioContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link AudioContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the Audio.
     * @param mimeType   the mime type of the Audio.
     */
    public AudioContent(String base64Data, String mimeType) {
        this.audio = Audio.builder()
                .base64Data(ensureNotBlank(base64Data, "base64data"))
                .mimeType(ensureNotBlank(mimeType, "mimeType")).build();
    }

    /**
     * Create a new {@link AudioContent} from the given Audio.
     *
     * @param audio the audio.
     */
    public AudioContent(Audio audio) {
        this.audio = audio;
    }

    /**
     * Get the {@code Audio}.
     *
     * @return the {@code Audio}.
     */
    public Audio audio() {
        return audio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioContent that = (AudioContent) o;
        return Objects.equals(this.audio, that.audio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(audio);
    }

    @Override
    public String toString() {
        return "AudioContent {" +
                " audio = " + audio +
                " }";
    }

    /**
     * Create a new {@link AudioContent} from the given url.
     *
     * @param url the url of the Audio.
     * @return the new {@link AudioContent}.
     */
    public static AudioContent from(URI url) {
        return new AudioContent(url);
    }

    /**
     * Create a new {@link AudioContent} from the given url.
     *
     * @param url the url of the Audio.
     * @return the new {@link AudioContent}.
     */
    public static AudioContent from(String url) {
        return new AudioContent(url);
    }

    /**
     * Create a new {@link AudioContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the Audio.
     * @param mimeType   the mime type of the Audio.
     * @return the new {@link AudioContent}.
     */
    public static AudioContent from(String base64Data, String mimeType) {
        return new AudioContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link AudioContent} from the given Audio.
     *
     * @param audio the Audio.
     * @return the new {@link AudioContent}.
     */
    public static AudioContent from(Audio audio) {
        return new AudioContent(audio);
    }
}
