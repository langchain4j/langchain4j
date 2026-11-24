package dev.langchain4j.data.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

class MediaFetcherTest {

    @Test
    void fetchAsync_should_fail_by_default_and_explain_how_to_implement_it() {
        MediaFetcher mediaFetcher = request -> FetchedMedia.from(new byte[1]);

        CompletableFuture<FetchedMedia> media =
                mediaFetcher.fetchAsync(MediaFetchRequest.from(URI.create("https://example.com/cat.png")));

        assertThat(media)
                .failsWithin(1, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(AsyncNotSupportedException.class)
                .withMessageStartingWith("fetchAsync() is not implemented by " + mediaFetcher.getClass().getName())
                .withMessageContaining("implement fetchAsync(MediaFetchRequest)")
                .withMessageContaining("CompletableFuture.supplyAsync(() -> fetch(request), executor)");
    }

    @Test
    void fetchAsync_can_be_implemented_as_shown_in_the_javadoc() {
        MediaFetcher mediaFetcher = new MediaFetcher() {

            @Override
            public FetchedMedia fetch(MediaFetchRequest request) {
                return FetchedMedia.from(new byte[] {1, 2, 3}, "image/png");
            }

            @Override
            public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
                return CompletableFuture.supplyAsync(() -> fetch(request), ForkJoinPool.commonPool());
            }
        };

        assertThat(mediaFetcher.fetchAsync(MediaFetchRequest.from(URI.create("https://example.com/cat.png"))))
                .succeedsWithin(5, SECONDS)
                .isEqualTo(FetchedMedia.from(new byte[] {1, 2, 3}, "image/png"));
    }

    /**
     * The example of the Javadoc of {@link MediaFetcher}, with {@code readFromStorage(URI)} filled in.
     */
    static class StorageMediaFetcher implements MediaFetcher {

        private final MediaFetcher defaultMediaFetcher = DefaultMediaFetcher.builder().build();
        private final Executor executor = Executors.newFixedThreadPool(4);

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            if (isInStorage(request)) {
                return FetchedMedia.from(readFromStorage(request.url()), "image/png");
            }
            return defaultMediaFetcher.fetch(request);
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            if (isInStorage(request)) {
                return CompletableFuture.supplyAsync(() -> fetch(request), executor);
            }
            return defaultMediaFetcher.fetchAsync(request);
        }

        private static boolean isInStorage(MediaFetchRequest request) {
            return request != null && "storage.internal.example.com".equals(request.url().getHost());
        }

        private static byte[] readFromStorage(URI url) {
            return url.getPath().getBytes();
        }
    }

    @Test
    void the_example_of_the_javadoc_should_serve_its_own_urls_and_pass_the_others_to_the_default_media_fetcher() {
        MediaFetcher mediaFetcher = new StorageMediaFetcher();
        MediaFetchRequest inStorage =
                MediaFetchRequest.from(URI.create("https://storage.internal.example.com/cat.png"));
        MediaFetchRequest localFile = MediaFetchRequest.from(URI.create("file:///etc/passwd"));

        assertThat(mediaFetcher.fetch(inStorage)).isEqualTo(FetchedMedia.from("/cat.png".getBytes(), "image/png"));
        assertThat(mediaFetcher.fetchAsync(inStorage))
                .succeedsWithin(5, SECONDS)
                .isEqualTo(FetchedMedia.from("/cat.png".getBytes(), "image/png"));
        assertThatThrownBy(() -> mediaFetcher.fetch(localFile)).isExactlyInstanceOf(InvalidRequestException.class);
        assertThat(mediaFetcher.fetchAsync(localFile))
                .failsWithin(5, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(InvalidRequestException.class);
        assertThat(mediaFetcher.fetchAsync(null))
                .failsWithin(5, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
