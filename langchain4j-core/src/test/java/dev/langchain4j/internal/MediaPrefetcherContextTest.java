package dev.langchain4j.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.spi.CapturedContext;
import dev.langchain4j.spi.ExecutorProvider;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Tests that the code that continues after the downloads runs with the context of the thread that started them.
 * {@link ExecutorProvider#set(ExecutorProvider)} is process-wide, so this class runs alone and sequentially.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class MediaPrefetcherContextTest {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private static final URI CAT = URI.create("https://example.com/cat.png");
    private static final FetchedMedia MEDIA = FetchedMedia.from("cat".getBytes(UTF_8), "image/png");

    /**
     * Captures the request ID of the calling thread and restores it in wrapped tasks.
     */
    private static final class RequestIdExecutorProvider implements ExecutorProvider {

        @Override
        public Executor executor() {
            return null;
        }

        @Override
        public CapturedContext captureContext() {
            String captured = REQUEST_ID.get();
            return task -> () -> {
                String previous = REQUEST_ID.get();
                REQUEST_ID.set(captured);
                try {
                    task.run();
                } finally {
                    REQUEST_ID.set(previous);
                }
            };
        }
    }

    /**
     * A fetcher whose download completes when the test completes it, on another thread without a request ID.
     */
    private static final class ControlledMediaFetcher implements MediaFetcher {

        private final CompletableFuture<FetchedMedia> download = new CompletableFuture<>();

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            throw new AssertionError("must not be called");
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            return download;
        }

        private void completeOnAnotherThread(Runnable completion) throws InterruptedException {
            AtomicReference<String> requestIdAfterwards = new AtomicReference<>("not run");
            Thread thread = new Thread(() -> {
                completion.run();
                requestIdAfterwards.set(REQUEST_ID.get());
            });
            thread.start();
            thread.join(10_000);
            assertThat(requestIdAfterwards.get())
                    .as("the context is restored on the completing thread")
                    .isNull();
        }
    }

    @BeforeEach
    void registerExecutorProvider() {
        ExecutorProvider.set(new RequestIdExecutorProvider());
    }

    @AfterEach
    void cleanUp() {
        ExecutorProvider.set(null);
        REQUEST_ID.remove();
    }

    @Test
    void should_continue_after_the_downloads_in_the_context_of_the_calling_thread() throws Exception {
        ControlledMediaFetcher fetcher = new ControlledMediaFetcher();

        REQUEST_ID.set("request-42");
        CompletableFuture<String> requestIdInContinuation =
                MediaPrefetcher.prefetch(fetcher, List.of(CAT)).thenApply(prefetched -> REQUEST_ID.get());
        REQUEST_ID.remove();

        fetcher.completeOnAnotherThread(() -> fetcher.download.complete(MEDIA));

        assertThat(requestIdInContinuation).succeedsWithin(10, SECONDS).isEqualTo("request-42");
    }

    @Test
    void should_report_a_failed_download_in_the_context_of_the_calling_thread() throws Exception {
        ControlledMediaFetcher fetcher = new ControlledMediaFetcher();

        REQUEST_ID.set("request-42");
        CompletableFuture<String> requestIdInContinuation =
                MediaPrefetcher.prefetch(fetcher, List.of(CAT)).handle((prefetched, error) -> REQUEST_ID.get());
        REQUEST_ID.remove();

        fetcher.completeOnAnotherThread(
                () -> fetcher.download.completeExceptionally(new InvalidRequestException("not allowed")));

        assertThat(requestIdInContinuation).succeedsWithin(10, SECONDS).isEqualTo("request-42");
    }

    @Test
    void prefetchThenStream_should_start_the_stream_and_report_failures_in_the_context_of_the_calling_thread()
            throws Exception {
        ControlledMediaFetcher succeeding = new ControlledMediaFetcher();
        ControlledMediaFetcher failing = new ControlledMediaFetcher();
        CompletableFuture<String> requestIdInStream = new CompletableFuture<>();
        CompletableFuture<String> requestIdInOnError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                requestIdInOnError.complete(REQUEST_ID.get());
            }
        };

        REQUEST_ID.set("request-42");
        MediaPrefetcher.prefetchThenStream(
                succeeding, List.of(CAT), handler, prefetched -> requestIdInStream.complete(REQUEST_ID.get()));
        MediaPrefetcher.prefetchThenStream(failing, List.of(CAT), handler, prefetched -> {});
        REQUEST_ID.remove();

        succeeding.completeOnAnotherThread(() -> succeeding.download.complete(MEDIA));
        failing.completeOnAnotherThread(
                () -> failing.download.completeExceptionally(new InvalidRequestException("not allowed")));

        assertThat(requestIdInStream).succeedsWithin(10, SECONDS).isEqualTo("request-42");
        assertThat(requestIdInOnError).succeedsWithin(10, SECONDS).isEqualTo("request-42");
    }

    @Test
    void should_not_capture_the_context_without_urls() throws Exception {
        ExecutorProvider.set(new ExecutorProvider() {
            @Override
            public Executor executor() {
                return null;
            }

            @Override
            public CapturedContext captureContext() {
                throw new AssertionError("must not be called");
            }
        });

        assertThat(MediaPrefetcher.prefetch(new ControlledMediaFetcher(), List.of()))
                .succeedsWithin(10, SECONDS);
    }
}
