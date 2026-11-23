package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import dev.langchain4j.Internal;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.internal.MediaPrefetcher;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The media of one request, downloaded with a {@link MediaFetcher}: each URL is downloaded once, also when the request
 * needs it more than once (for example to check the format of an image first) or refers to it in several messages.
 * Every download of this module goes through this class. Not thread-safe: an instance is used for one request.
 */
@Internal
final class MediaDownloads {

    private final MediaFetcher mediaFetcher;
    private final Map<URI, FetchedMedia> downloaded = new HashMap<>();

    MediaDownloads(MediaFetcher mediaFetcher) {
        this.mediaFetcher = mediaFetcher;
    }

    FetchedMedia fetch(URI url) {
        return downloaded.computeIfAbsent(url, mediaFetcher::fetch);
    }

    /**
     * Downloads the given URLs with {@link MediaFetcher#fetchAsync(URI)}, without blocking the calling thread.
     *
     * @return a future of the downloaded media; it never throws, and cancelling it cancels the downloads.
     */
    static CompletableFuture<MediaDownloads> prefetch(MediaFetcher mediaFetcher, List<URI> urls) {
        CompletableFuture<MediaFetcher> prefetched = MediaPrefetcher.prefetch(mediaFetcher, urls);
        CompletableFuture<MediaDownloads> downloads = prefetched.thenApply(MediaDownloads::new);
        propagateCancellation(downloads, prefetched);
        return downloads;
    }
}
