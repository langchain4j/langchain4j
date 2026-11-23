package dev.langchain4j.data.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
}
