package dev.langchain4j.data.media;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.net.URI;

/**
 * A request to a {@link MediaFetcher}: the URL of the media (an image, audio, video or PDF file) to download.
 * <p>
 * Model integrations create it for each media URL that they need to download. To download media yourself, create it
 * with {@link #from(URI)}:
 * <pre>{@code
 * FetchedMedia media = mediaFetcher.fetch(MediaFetchRequest.from(URI.create("https://example.com/cat.png")));
 * }</pre>
 *
 * @since 1.25.0
 */
@Experimental
public final class MediaFetchRequest {

    private final URI url;

    private MediaFetchRequest(URI url) {
        this.url = ensureNotNull(url, "url");
    }

    /**
     * Creates a request to download the media at the given URL.
     *
     * @param url the URL of the media, for example {@code https://example.com/cat.png}.
     * @return the request.
     */
    public static MediaFetchRequest from(URI url) {
        return new MediaFetchRequest(url);
    }

    /**
     * Returns the URL of the media to download.
     *
     * @return the URL, never {@code null}.
     */
    public URI url() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaFetchRequest that = (MediaFetchRequest) o;
        return url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    /**
     * Shows the scheme and the host of the URL only. The path and the query are left out, because they can contain
     * credentials, for example in a presigned URL.
     */
    @Override
    public String toString() {
        return "MediaFetchRequest { url = " + quoted(withoutPathAndQuery(url)) + " }";
    }

    private static String withoutPathAndQuery(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme() + ":";
        if (url.getHost() == null) {
            return scheme + "...";
        }
        String port = url.getPort() == -1 ? "" : ":" + url.getPort();
        return scheme + "//" + url.getHost() + port + "/...";
    }
}
