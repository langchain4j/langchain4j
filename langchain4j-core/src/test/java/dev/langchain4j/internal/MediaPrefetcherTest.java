package dev.langchain4j.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.media.DefaultMediaFetcher;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssertAlternative;
import org.junit.jupiter.api.Test;

class MediaPrefetcherTest {

    private static final URI CAT = URI.create("https://example.com/cat.png");
    private static final URI DOG = URI.create("https://example.com/dog.png");
    private static final URI BIRD = URI.create("https://birds.example.com/bird.png");

    /**
     * A fetcher whose fetchAsync answers with the given function and records the URLs it was asked for.
     */
    private static final class RecordingMediaFetcher implements MediaFetcher {

        private final Function<URI, CompletableFuture<FetchedMedia>> answer;
        private final List<URI> urls = new CopyOnWriteArrayList<>();
        private final Map<URI, CompletableFuture<FetchedMedia>> futures = new ConcurrentHashMap<>();

        private RecordingMediaFetcher(Function<URI, CompletableFuture<FetchedMedia>> answer) {
            this.answer = answer;
        }

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            throw new AssertionError("prefetch must not call fetch()");
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            URI url = request.url();
            urls.add(url);
            CompletableFuture<FetchedMedia> future = answer.apply(url);
            if (future != null) {
                futures.put(url, future);
            }
            return future;
        }
    }

    private static FetchedMedia media(URI url) {
        return FetchedMedia.from(url.getPath().getBytes(), "image/png");
    }

    private static ThrowableAssertAlternative<?> assertFailsWith(CompletableFuture<?> future) {
        return assertThat(future)
                .failsWithin(5, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause();
    }

    @Test
    void should_fetch_each_distinct_url_once_and_serve_the_results() {
        RecordingMediaFetcher fetcher =
                new RecordingMediaFetcher(url -> CompletableFuture.supplyAsync(() -> media(url)));

        MediaFetcher prefetched = MediaPrefetcher.prefetch(fetcher, List.of(CAT, DOG, CAT, BIRD))
                .join();

        assertThat(fetcher.urls).containsExactlyInAnyOrder(CAT, DOG, BIRD);
        assertThat(prefetched.fetch(MediaFetchRequest.from(CAT))).isEqualTo(media(CAT));
        assertThat(prefetched.fetch(MediaFetchRequest.from(DOG))).isEqualTo(media(DOG));
        assertThat(prefetched.fetchAsync(MediaFetchRequest.from(BIRD))).isCompletedWithValue(media(BIRD));
        assertThat(fetcher.urls).hasSize(3);
    }

    @Test
    void should_fail_for_urls_that_were_not_prefetched() {
        RecordingMediaFetcher fetcher =
                new RecordingMediaFetcher(url -> CompletableFuture.completedFuture(media(url)));
        MediaFetcher prefetched = MediaPrefetcher.prefetch(fetcher, List.of(CAT)).join();

        assertThatThrownBy(() -> prefetched.fetch(MediaFetchRequest.from(BIRD)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("A media URL of host 'birds.example.com' was not prefetched");
        assertThatThrownBy(() -> prefetched.fetch(null))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("A media URL was not prefetched");
        assertFailsWith(prefetched.fetchAsync(MediaFetchRequest.from(BIRD)))
                .isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_not_call_the_fetcher_without_urls() {
        MediaFetcher syncOnly = request -> {
            throw new AssertionError("must not be called");
        };

        MediaFetcher prefetched = MediaPrefetcher.prefetch(syncOnly, List.of()).join();

        assertThatThrownBy(() -> prefetched.fetch(MediaFetchRequest.from(CAT)))
                .isExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_fail_with_AsyncNotSupportedException_for_a_fetcher_without_fetchAsync() {
        MediaFetcher syncOnly = request -> FetchedMedia.from(new byte[1]);

        assertFailsWith(MediaPrefetcher.prefetch(syncOnly, List.of(CAT)))
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync() is not implemented by");
    }

    @Test
    void should_fail_as_soon_as_one_download_fails_and_cancel_the_others() {
        InvalidRequestException notAllowed = new InvalidRequestException("not allowed");
        RecordingMediaFetcher fetcher = new RecordingMediaFetcher(url -> url.equals(DOG)
                ? CompletableFuture.supplyAsync(() -> {
                    throw notAllowed;
                })
                : new CompletableFuture<>());

        CompletableFuture<MediaFetcher> prefetched = MediaPrefetcher.prefetch(fetcher, List.of(CAT, DOG, BIRD));

        assertFailsWith(prefetched).isSameAs(notAllowed);
        assertThat(fetcher.futures.get(CAT))
                .failsWithin(5, SECONDS)
                .withThrowableOfType(CancellationException.class);
    }

    @Test
    void should_not_start_more_downloads_after_a_failure() {
        RecordingMediaFetcher fetcher = new RecordingMediaFetcher(
                url -> CompletableFuture.failedFuture(new InvalidRequestException("not allowed")));

        assertFailsWith(MediaPrefetcher.prefetch(fetcher, List.of(CAT, DOG, BIRD)))
                .isExactlyInstanceOf(InvalidRequestException.class);
        assertThat(fetcher.urls).containsExactly(CAT);
    }

    @Test
    void should_cancel_the_downloads_when_the_result_is_cancelled() {
        RecordingMediaFetcher fetcher = new RecordingMediaFetcher(url -> new CompletableFuture<>());
        CompletableFuture<MediaFetcher> prefetched = MediaPrefetcher.prefetch(fetcher, List.of(CAT, DOG));

        prefetched.cancel(true);

        assertThat(fetcher.futures.values()).hasSize(2).allSatisfy(future -> assertThat(future)
                .failsWithin(5, SECONDS)
                .withThrowableOfType(CancellationException.class));
    }

    @Test
    void should_never_throw() {
        MediaFetcher fetcher = request -> FetchedMedia.from(new byte[1]);

        assertFailsWith(MediaPrefetcher.prefetch(null, List.of(CAT)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("mediaFetcher");
        assertFailsWith(MediaPrefetcher.prefetch(fetcher, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .withMessageContaining("urls");
        assertFailsWith(MediaPrefetcher.prefetch(fetcher, Arrays.asList(CAT, null)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .withMessage("urls must not contain null");
    }

    @Test
    void should_fail_when_fetchAsync_breaks_its_contract() {
        RuntimeException thrown = new RuntimeException("thrown directly");
        RecordingMediaFetcher throwing = new RecordingMediaFetcher(url -> {
            throw thrown;
        });
        RecordingMediaFetcher returningNull = new RecordingMediaFetcher(url -> null);
        RecordingMediaFetcher completingWithNull =
                new RecordingMediaFetcher(url -> CompletableFuture.completedFuture(null));

        assertFailsWith(MediaPrefetcher.prefetch(throwing, List.of(CAT))).isSameAs(thrown);
        assertFailsWith(MediaPrefetcher.prefetch(returningNull, List.of(CAT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage(RecordingMediaFetcher.class.getName() + ".fetchAsync(MediaFetchRequest) returned null");
        assertFailsWith(MediaPrefetcher.prefetch(completingWithNull, List.of(CAT)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage(
                        RecordingMediaFetcher.class.getName() + ".fetchAsync(MediaFetchRequest) completed with null");
    }

    @Test
    void should_share_one_default_media_fetcher() {
        assertThat(MediaPrefetcher.defaultMediaFetcher())
                .isInstanceOf(DefaultMediaFetcher.class)
                .isSameAs(MediaPrefetcher.defaultMediaFetcher());
    }

    @Test
    void should_prefetch_with_the_default_media_fetcher() {
        assertFailsWith(MediaPrefetcher.prefetch(
                        MediaPrefetcher.defaultMediaFetcher(), List.of(URI.create("file:///etc/passwd"))))
                .isExactlyInstanceOf(InvalidRequestException.class)
                .withMessageContaining("Only http and https URLs can be fetched");
    }
}
