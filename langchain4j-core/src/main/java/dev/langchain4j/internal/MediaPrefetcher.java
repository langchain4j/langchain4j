package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.media.DefaultMediaFetcher;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helps model integrations that download media with a {@link MediaFetcher}.
 * <p>
 * {@link #defaultMediaFetcher()} is the {@link DefaultMediaFetcher} that they use when no fetcher is configured. It is
 * shared by all of them, so that its HTTP client and its threads exist only once.
 * <p>
 * {@link #prefetch(MediaFetcher, Collection)} lets their asynchronous and streaming paths download every media URL of
 * a request with {@link MediaFetcher#fetchAsync(MediaFetchRequest)}, without blocking, and then map the request with
 * their existing synchronous code, which calls {@link MediaFetcher#fetch(MediaFetchRequest)} of the fetcher that it
 * returns:
 * <pre>{@code
 * CompletableFuture<ProviderRequest> providerRequest = MediaPrefetcher.prefetch(mediaFetcher, mediaUrls)
 *         .thenApply(prefetched -> toProviderRequest(chatRequest, prefetched));
 * }</pre>
 */
@Internal
public final class MediaPrefetcher {

    private MediaPrefetcher() {}

    /**
     * Returns the {@link DefaultMediaFetcher} with the default settings that model integrations use when no fetcher is
     * configured. It is created on the first call, and its HTTP client on the first download.
     *
     * @return the shared default fetcher.
     */
    public static MediaFetcher defaultMediaFetcher() {
        return DefaultInstance.MEDIA_FETCHER;
    }

    /**
     * Downloads the given URLs with {@link MediaFetcher#fetchAsync(MediaFetchRequest)}, all at the same time and each
     * distinct URL once. Without URLs, the fetcher is not called at all.
     *
     * @param mediaFetcher the fetcher that downloads the media.
     * @param urls         the URLs to download.
     * @return a future of a fetcher that returns the downloaded media of these URLs, from
     *         {@link MediaFetcher#fetch(MediaFetchRequest)} and {@link MediaFetcher#fetchAsync(MediaFetchRequest)},
     *         and fails with an {@link IllegalStateException} for any other URL. The future fails as soon as one
     *         download fails, with the exception of that download, and the other downloads are then cancelled;
     *         cancelling the future cancels the downloads. This method never throws.
     */
    public static CompletableFuture<MediaFetcher> prefetch(MediaFetcher mediaFetcher, Collection<URI> urls) {
        try {
            ensureNotNull(mediaFetcher, "mediaFetcher");
            Set<URI> distinctUrls = new LinkedHashSet<>(ensureNotNull(urls, "urls"));
            if (distinctUrls.contains(null)) {
                throw illegalArgument("urls must not contain null");
            }
            return prefetchAll(mediaFetcher, distinctUrls);
        } catch (Throwable e) {
            restoreInterruptIfInterrupted(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletableFuture<MediaFetcher> prefetchAll(MediaFetcher mediaFetcher, Set<URI> urls) {
        CompletableFuture<MediaFetcher> result = new CompletableFuture<>();
        if (urls.isEmpty()) {
            result.complete(new PrefetchedMediaFetcher(Map.of()));
            return result;
        }
        Map<URI, FetchedMedia> fetched = new ConcurrentHashMap<>();
        AtomicInteger remaining = new AtomicInteger(urls.size());
        List<CompletableFuture<FetchedMedia>> downloads = new ArrayList<>(urls.size());
        for (URI url : urls) {
            if (result.isDone()) {
                break;
            }
            CompletableFuture<FetchedMedia> download = failOnThrow(
                    mediaFetcher,
                    "fetchAsync(MediaFetchRequest)",
                    () -> mediaFetcher.fetchAsync(MediaFetchRequest.from(url)));
            downloads.add(download);
            download.whenComplete((media, error) -> {
                if (error != null) {
                    result.completeExceptionally(unwrapCompletionException(error));
                } else if (media == null) {
                    result.completeExceptionally(new IllegalStateException(
                            mediaFetcher.getClass().getName() + ".fetchAsync(MediaFetchRequest) completed with null"));
                } else {
                    fetched.put(url, media);
                    if (remaining.decrementAndGet() == 0) {
                        result.complete(new PrefetchedMediaFetcher(Map.copyOf(fetched)));
                    }
                }
            });
        }
        result.whenComplete((prefetched, error) -> {
            if (error != null) {
                downloads.forEach(download -> download.cancel(true));
            }
        });
        return result;
    }

    private static final class PrefetchedMediaFetcher implements MediaFetcher {

        private final Map<URI, FetchedMedia> media;

        private PrefetchedMediaFetcher(Map<URI, FetchedMedia> media) {
            this.media = media;
        }

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            FetchedMedia fetched = request == null ? null : media.get(request.url());
            if (fetched == null) {
                throw new IllegalStateException("A media URL"
                        + (request == null ? "" : " of host '" + request.url().getHost() + "'")
                        + " was not prefetched");
            }
            return fetched;
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            try {
                return CompletableFuture.completedFuture(fetch(request));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    private static final class DefaultInstance {

        private static final MediaFetcher MEDIA_FETCHER = DefaultMediaFetcher.builder().build();
    }
}
