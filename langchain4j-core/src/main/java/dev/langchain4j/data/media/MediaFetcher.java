package dev.langchain4j.data.media;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.internal.AsyncNotSupported;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads media (an image, audio, video or PDF file) from a URL.
 * <p>
 * Some model providers accept media only as content, not as a URL. When a message for such a provider contains a
 * media URL, for example an {@link ImageContent} created with {@link ImageContent#from(URI)}, its LangChain4j
 * integration downloads the media with a {@code MediaFetcher} and sends the content. Unless another fetcher is
 * configured, it uses a {@link DefaultMediaFetcher}, which downloads only {@code http} and {@code https} URLs of
 * public hosts, with a timeout and a size limit.
 * <p>
 * Implement this interface to download media in another way, for example from a private storage with its own client.
 * Media URLs can come from end users, from documents, or from tools that a model calls, so an implementation must
 * not download what it should not, such as local files or internal services. A simple way is to handle only the URLs
 * you trust yourself, and to pass every other URL to a {@link DefaultMediaFetcher}:
 * <pre>{@code
 * MediaFetcher defaultMediaFetcher = DefaultMediaFetcher.builder().build();
 *
 * MediaFetcher mediaFetcher = url -> {
 *     if ("storage.internal.example.com".equals(url.getHost())) {
 *         return FetchedMedia.from(readFromStorage(url), "image/png");
 *     }
 *     return defaultMediaFetcher.fetch(url);
 * };
 * }</pre>
 * A fetcher written as a lambda, like this one, implements only {@link #fetch(URI)}; asynchronous and streaming model
 * calls need {@link #fetchAsync(URI)} as well.
 * <p>
 * Implementations are used by many threads at the same time and must be thread-safe.
 *
 * @since 1.25.0
 */
@Experimental
@FunctionalInterface
public interface MediaFetcher {

    /**
     * Downloads the media at the given URL.
     *
     * @param url the URL of the media, for example {@code https://example.com/cat.png}.
     * @return the downloaded media.
     * @throws InvalidRequestException if the URL is not allowed (for example a local file), or if the host answers
     *                                 with an HTTP status other than 2xx.
     * @throws LangChain4jException    if the download fails, for example because the host cannot be reached or the
     *                                 download takes too long.
     */
    FetchedMedia fetch(URI url);

    /**
     * Downloads the media at the given URL without blocking the calling thread. This is the non-blocking counterpart
     * of {@link #fetch(URI)}, used by the asynchronous and streaming calls of model integrations.
     * <p>
     * The default returns a future failed with an {@link AsyncNotSupportedException}, so that these calls fail instead
     * of blocking a thread that must not be blocked. A fetcher whose {@link #fetch(URI)} blocks can implement this method
     * by running {@link #fetch(URI)} on an executor of its choice:
     * <pre>{@code
     * public CompletableFuture<FetchedMedia> fetchAsync(URI url) {
     *     return CompletableFuture.supplyAsync(() -> fetch(url), executor);
     * }
     * }</pre>
     * An implementation must not throw and must not return {@code null}: every failure, including an invalid argument,
     * completes the returned future exceptionally, with the same exceptions as {@link #fetch(URI)}.
     *
     * @param url the URL of the media, for example {@code https://example.com/cat.png}.
     * @return a future of the downloaded media.
     */
    default CompletableFuture<FetchedMedia> fetchAsync(URI url) {
        return AsyncNotSupported.failedFuture("fetchAsync() is not implemented by " + getClass().getName()
                + ". If fetch() blocks, implement fetchAsync() by running it on an executor of your choice,"
                + " for example: return CompletableFuture.supplyAsync(() -> fetch(url), executor)");
    }
}
