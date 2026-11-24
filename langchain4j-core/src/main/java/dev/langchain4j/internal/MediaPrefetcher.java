package dev.langchain4j.internal;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.media.DefaultMediaFetcher;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

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
 * {@link #prefetchThenStream(MediaFetcher, Collection, StreamingChatResponseHandler, Consumer)} does the same for a
 * streaming path, and reports every failure to the {@link StreamingChatResponseHandler}.
 * <p>
 * The downloads complete on other threads. So that the code that continues after them runs with the context of the
 * thread that started them (for example a tracing span or the MDC), the returned futures are completed in the
 * context that {@link ExecutorProvider#captureContext()} captured on that thread: a continuation registered without
 * an executor ({@code thenApply}, {@code whenComplete}, ...) runs in that context.
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
     *         cancelling the future cancels the downloads. It is completed in the context captured on the calling
     *         thread, see the class documentation. This method never throws.
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

    /**
     * Downloads the given URLs like {@link #prefetch(MediaFetcher, Collection)}, then starts a stream with the
     * downloaded media. This is the streaming counterpart of {@link #prefetch(MediaFetcher, Collection)}, for a
     * streaming path that does not block the calling thread and reports its results to a
     * {@link StreamingChatResponseHandler}:
     * <pre>{@code
     * MediaPrefetcher.prefetchThenStream(mediaFetcher, mediaUrls, handler,
     *         prefetched -> client.stream(toProviderRequest(chatRequest, prefetched), handler));
     * }</pre>
     * If a download fails, or {@code stream} throws, for example while it maps the request, the handler receives
     * the exception in {@link StreamingChatResponseHandler#onError(Throwable)}. An exception thrown by
     * {@code onError} itself is logged and ignored.
     *
     * @param mediaFetcher the fetcher that downloads the media.
     * @param urls         the URLs to download.
     * @param handler      the handler that receives the failures.
     * @param stream       starts the stream with a fetcher that returns the downloaded media, see
     *                     {@link #prefetch(MediaFetcher, Collection)}. It runs in the context captured on the calling
     *                     thread.
     * @return the future of the downloads. Cancelling it cancels the downloads, and the stream is then not started:
     *         the handler receives a {@link CancellationException} in
     *         {@link StreamingChatResponseHandler#onError(Throwable)}. This method never throws.
     */
    public static CompletableFuture<MediaFetcher> prefetchThenStream(
            MediaFetcher mediaFetcher,
            Collection<URI> urls,
            StreamingChatResponseHandler handler,
            Consumer<MediaFetcher> stream) {
        CompletableFuture<MediaFetcher> prefetched = prefetch(mediaFetcher, urls);
        prefetched.whenComplete((media, error) -> {
            if (error != null) {
                withLoggingExceptions(() -> handler.onError(unwrapCompletionException(error)));
                return;
            }
            try {
                stream.accept(media);
            } catch (Throwable e) {
                restoreInterruptIfInterrupted(e);
                withLoggingExceptions(() -> handler.onError(e));
            }
        });
        return prefetched;
    }

    private static CompletableFuture<MediaFetcher> prefetchAll(MediaFetcher mediaFetcher, Set<URI> urls) {
        CompletableFuture<MediaFetcher> result = new CompletableFuture<>();
        if (urls.isEmpty()) {
            result.complete(new PrefetchedMediaFetcher(Map.of()));
            return result;
        }
        CapturedContext capturedContext = DefaultExecutorProvider.captureContext();
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
                    runIn(capturedContext, () -> result.completeExceptionally(unwrapCompletionException(error)));
                } else if (media == null) {
                    runIn(capturedContext, () -> result.completeExceptionally(new IllegalStateException(
                            mediaFetcher.getClass().getName() + ".fetchAsync(MediaFetchRequest) completed with null")));
                } else {
                    fetched.put(url, media);
                    if (remaining.decrementAndGet() == 0) {
                        runIn(capturedContext, () -> result.complete(new PrefetchedMediaFetcher(Map.copyOf(fetched))));
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

    /**
     * Runs the task in the captured context, so that the continuations that completing a future runs synchronously
     * run in that context too.
     */
    private static void runIn(@Nullable CapturedContext capturedContext, Runnable task) {
        if (capturedContext == null) {
            task.run();
        } else {
            capturedContext.wrap(task).run();
        }
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
