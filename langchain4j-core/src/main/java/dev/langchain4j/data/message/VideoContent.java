package dev.langchain4j.data.message;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.video.Video;

import java.net.URI;
import java.util.Objects;

import static dev.langchain4j.data.message.ContentType.VIDEO;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class VideoContent implements Content {

    private final Video video;

    @Override
    public ContentType type() {
        return VIDEO;
    }


    /**
     * Create a new {@link VideoContent} from the given url.
     *
     * @param url the url of the Video.
     */
    public VideoContent(URI url) {
        this.video = Video.builder()
            .url(ensureNotNull(url, "url"))
            .build();
    }

    /**
     * Create a new {@link VideoContent} from the given url.
     *
     * @param url the url of the video.
     */
    public VideoContent(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new {@link VideoContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the video.
     * @param mimeType the mime type of the video.
     */
    public VideoContent(String base64Data, String mimeType) {
        this.video = Video.builder()
            .base64Data(ensureNotBlank(base64Data, "base64data"))
            .mimeType(ensureNotBlank(mimeType, "mimeType")).build();
    }

    /**
     * Create a new {@link VideoContent} from the given video.
     *
     * @param video the video.
     */
    public VideoContent(Video video) {
        this.video = video;
    }

    /**
     * Get the {@code Video}.
     * @return the {@code Video}.
     */
    public Video video() {
        return video;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoContent that = (VideoContent) o;
        return Objects.equals(this.video, that.video);
    }

    @Override
    public int hashCode() {
        return Objects.hash(video);
    }

    @Override
    public String toString() {
        return "VideoContent {" +
            " video = " + video +
            " }";
    }

    /**
     * Create a new {@link VideoContent} from the given url.
     *
     * @param url the url of the video.
     * @return the new {@link VideoContent}.
     */
    public static VideoContent from(URI url) {
        return new VideoContent(url);
    }

    /**
     * Create a new {@link VideoContent} from the given url.
     *
     * @param url the url of the video.
     * @return the new {@link VideoContent}.
     */
    public static VideoContent from(String url) {
        return new VideoContent(url);
    }

    /**
     * Create a new {@link VideoContent} from the given base64 data and mime type.
     *
     * @param base64Data the base64 data of the video.
     * @param mimeType the mime type of the video.
     * @return the new {@link VideoContent}.
     */
    public static VideoContent from(String base64Data, String mimeType) {
        return new VideoContent(base64Data, mimeType);
    }

    /**
     * Create a new {@link VideoContent} from the given video.
     *
     * @param video the video.
     * @return the new {@link VideoContent}.
     */
    public static VideoContent from(Video video) {
        return new VideoContent(video);
    }
}
