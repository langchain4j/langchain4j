package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * The media of one request, downloaded with a {@link MediaFetcher}: each URL is downloaded once, also when the request
 * needs it more than once (for example to check the format of an image first) or refers to it in several messages.
 * Every download of this module goes through this class. It also names the documents of the request that have no URL.
 * Not thread-safe: an instance is used for one request.
 */
@Internal
final class MediaDownloads {

    private final MediaFetcher mediaFetcher;
    private final Map<URI, FetchedMedia> downloaded = new HashMap<>();
    private final Map<String, Integer> documentNames = new HashMap<>();

    MediaDownloads(MediaFetcher mediaFetcher) {
        this.mediaFetcher = mediaFetcher;
    }

    FetchedMedia fetch(URI url) {
        return downloaded.computeIfAbsent(url, u -> mediaFetcher.fetch(MediaFetchRequest.from(u)));
    }

    /**
     * Returns the name of a document that has no URL, for example one created from a file or from base64 data:
     * {@code document-} and the first 12 hex digits of the SHA-256 hash of its content. The same document gets the
     * same name in every request, so that prompt caching keeps working. When the same name appears again in this
     * request, it gets the suffix {@code -2}, then {@code -3}, and so on, so that the names in a request are distinct.
     */
    String documentName(byte[] content) {
        String name = "document-" + HexFormat.of().formatHex(sha256(content)).substring(0, 12);
        int count = documentNames.merge(name, 1, Integer::sum);
        return count == 1 ? name : name + "-" + count;
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java platform supports SHA-256
        }
    }
}
