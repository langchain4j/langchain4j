package dev.langchain4j.data.media;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;

/**
 * The {@link MediaFetcher} that LangChain4j uses unless another one is configured. It downloads media (an image, audio,
 * video or PDF file) from a URL with the JDK {@link HttpClient}, and downloads only what is safe to download from
 * inside your network, because media URLs can come from end users, from documents, or from tools that a model calls:
 * <ul>
 *     <li>Only {@code http} and {@code https} URLs. Local files ({@code file:} URLs and plain paths) and all other
 *     schemes are rejected. To send a local file, read it yourself, for example with
 *     {@link ImageContent#from(Path, String)} or {@link PdfFileContent#from(Path)}.</li>
 *     <li>Only hosts whose addresses are all public. Loopback, private, link-local (including the cloud metadata
 *     address {@code 169.254.169.254}), multicast and other special-purpose addresses are rejected, also when they are
 *     written in another form or reached through a redirect. To download from your own servers, list them in
 *     {@link Builder#allowNonPublicHosts(String...)}.</li>
 *     <li>If you set {@link Builder#allowedHosts(String...)}, only these hosts.</li>
 *     <li>Redirects are followed up to {@link Builder#maxRedirects(Integer)} times, and every URL they lead to is
 *     checked again. A redirect from {@code https} to {@code http} is rejected.</li>
 *     <li>One {@link Builder#timeout(Duration)} for the whole download, and a size limit
 *     ({@link Builder#maxSizeInBytes(Integer)}).</li>
 *     <li>No cookies and no credentials are sent, compressed content is not requested, and the JVM-wide proxy settings
 *     are not used (see {@link Builder#httpClientBuilder(HttpClient.Builder)}).</li>
 * </ul>
 * For example, a fetcher that downloads only from your CDN, up to 5 MiB:
 * <pre>{@code
 * MediaFetcher mediaFetcher = DefaultMediaFetcher.builder()
 *         .allowedHosts("images.example.com", "*.cdn.example.com")
 *         .maxSizeInBytes(5 * 1024 * 1024)
 *         .build();
 *
 * FetchedMedia media = mediaFetcher.fetch(URI.create("https://images.example.com/cat.png"));
 * }</pre>
 * How addresses are checked: the fetcher resolves the host name itself and checks every address it gets. A plain
 * {@code http} request is then sent to exactly the address that was checked, so a DNS answer that changes between the
 * check and the connection has no effect. For {@code https}, the HTTP client resolves the host name again when it
 * connects; a server reached that way cannot complete the connection, because it has no valid certificate for the
 * host name. Note that a public address can still belong to a service that trusts requests from your servers:
 * when the URLs come from end users or models, set {@link Builder#allowedHosts(String...)} where you can.
 * <p>
 * An instance is thread-safe and is meant to be shared. It starts its own threads on the first call of
 * {@link #fetch(URI)} or {@link #fetchAsync(URI)}, and creates its {@link HttpClient} for the first request that it
 * sends. The host lookups and the checks run on these threads, so {@link #fetchAsync(URI)} never blocks the calling
 * thread.
 *
 * @since 1.25.0
 */
@Experimental
public final class DefaultMediaFetcher implements MediaFetcher {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_MAX_SIZE_IN_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "LangChain4j";
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final @Nullable HostPatterns allowedHosts;
    private final HostPatterns allowNonPublicHosts;
    private final Duration connectTimeout;
    private final Duration timeout;
    private final int maxSizeInBytes;
    private final int maxRedirects;
    private final HttpClient.@Nullable Builder httpClientBuilder;
    private final HostResolver hostResolver;
    private final Predicate<InetAddress> isPublicAddress;

    private final Object lock = new Object();
    private volatile @Nullable ExecutorService executor;
    private volatile @Nullable HttpClient httpClient;

    private DefaultMediaFetcher(Builder builder) {
        this(builder, InetAddress::getAllByName, AddressClassifier::isPublic);
    }

    DefaultMediaFetcher(Builder builder, HostResolver hostResolver, Predicate<InetAddress> isPublicAddress) {
        if (builder.allowedHosts != null && builder.allowedHosts.isEmpty()) {
            throw illegalArgument("allowedHosts must not be empty. Set it to null to allow every host");
        }
        this.allowedHosts =
                builder.allowedHosts == null ? null : HostPatterns.from(builder.allowedHosts, "allowedHosts");
        this.allowNonPublicHosts = builder.allowNonPublicHosts == null
                ? HostPatterns.none()
                : HostPatterns.from(builder.allowNonPublicHosts, "allowNonPublicHosts");
        this.connectTimeout =
                ensurePositive(getOrDefault(builder.connectTimeout, DEFAULT_CONNECT_TIMEOUT), "connectTimeout");
        this.timeout = ensurePositive(getOrDefault(builder.timeout, DEFAULT_TIMEOUT), "timeout");
        this.maxSizeInBytes =
                ensureGreaterThanZero(getOrDefault(builder.maxSizeInBytes, DEFAULT_MAX_SIZE_IN_BYTES), "maxSizeInBytes");
        this.maxRedirects = ensureNotNegative(getOrDefault(builder.maxRedirects, DEFAULT_MAX_REDIRECTS), "maxRedirects");
        this.httpClientBuilder = builder.httpClientBuilder;
        this.hostResolver = hostResolver;
        this.isPublicAddress = isPublicAddress;
    }

    private static Duration ensurePositive(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw illegalArgument("%s must be positive, but is: %s", name, duration);
        }
        return duration;
    }

    /**
     * Creates a builder of a {@link DefaultMediaFetcher}. Every option has a default, so
     * {@code DefaultMediaFetcher.builder().build()} is a fetcher with the default settings.
     *
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Downloads the media at the given URL, checking it and every URL that a redirect leads to as described in the
     * class documentation.
     *
     * @param url the URL of the media, for example {@code https://example.com/cat.png}.
     * @return the downloaded media, with the MIME type from the {@code Content-Type} header of the response.
     * @throws InvalidRequestException if the URL or a URL that a redirect leads to is not allowed, if the content is
     *                                 larger than {@link Builder#maxSizeInBytes(Integer)}, if there are more than
     *                                 {@link Builder#maxRedirects(Integer)} redirects, or if the host answers with an
     *                                 HTTP status other than 2xx.
     * @throws LangChain4jException    if the download fails, for example because the host is unknown or cannot be
     *                                 reached, or does not complete within {@link Builder#timeout(Duration)}.
     */
    @Override
    public FetchedMedia fetch(URI url) {
        CompletableFuture<FetchedMedia> media = fetchAsync(url);
        try {
            return media.get();
        } catch (InterruptedException e) {
            media.cancel(true);
            Thread.currentThread().interrupt();
            throw new LangChain4jException("Interrupted while fetching media", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new LangChain4jException(cause);
        }
    }

    /**
     * Downloads the media at the given URL like {@link #fetch(URI)}, without blocking the calling thread: the host
     * lookups and checks run on the fetcher's own threads. Cancelling the returned future stops the download.
     *
     * @param url the URL of the media, for example {@code https://example.com/cat.png}.
     * @return a future of the downloaded media. It fails with the exceptions described in {@link #fetch(URI)}.
     */
    @Override
    public CompletableFuture<FetchedMedia> fetchAsync(URI url) {
        try {
            ensureNotNull(url, "url");
            Fetch fetch = new Fetch(url);
            executor().execute(() -> sendRequest(fetch, url, 0));
            return fetch.result;
        } catch (Throwable e) {
            restoreInterruptIfInterrupted(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void sendRequest(Fetch fetch, URI url, int redirects) {
        if (fetch.result.isDone()) {
            return;
        }
        try {
            String host = checkUrl(url);
            fetch.host = host;
            if (fetch.result.isDone()) {
                return;
            }
            HttpRequest request = HttpRequest.newBuilder(url)
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .build();
            CompletableFuture<HttpResponse<byte[]>> response = httpClient().sendAsync(request, this::bodySubscriber);
            fetch.inFlight(response);
            response.whenCompleteAsync(
                    (r, error) -> {
                        if (error != null) {
                            fetch.fail(toFetchException(error, host));
                        } else {
                            handleResponse(fetch, url, host, redirects, r);
                        }
                    },
                    executor());
        } catch (Throwable e) {
            fetch.fail(e instanceof Error ? e : toFetchException(e, fetch.host));
        }
    }

    private void handleResponse(Fetch fetch, URI url, String host, int redirects, HttpResponse<byte[]> response) {
        try {
            int status = response.statusCode();
            if (isRedirect(status)) {
                sendRequest(fetch, redirectTarget(url, host, response, redirects), redirects + 1);
            } else if (isSuccessful(status)) {
                fetch.result.complete(FetchedMedia.from(response.body(), mimeType(response)));
            } else {
                fetch.fail(new InvalidRequestException("Host '" + host + "' answered with HTTP status " + status));
            }
        } catch (Throwable e) {
            fetch.fail(e instanceof Error ? e : toFetchException(e, host));
        }
    }

    private String checkUrl(URI url) {
        String scheme = url.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidRequestException("Only http and https URLs can be fetched, but got "
                    + (scheme == null ? "a URL without a scheme" : "a URL with the scheme '" + scheme + "'")
                    + ". To send a local file, read it yourself, for example with ImageContent.from(Path, String)");
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidRequestException("The media URL has no valid host");
        }
        String normalizedHost = HostPatterns.normalize(host);
        if (allowedHosts != null && !allowedHosts.matches(normalizedHost)) {
            throw new InvalidRequestException("Host '" + host + "' is not in allowedHosts of the DefaultMediaFetcher");
        }
        if (!allowNonPublicHosts.matches(normalizedHost)) {
            publicAddresses(host);
        }
        return host;
    }

    private InetAddress[] publicAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new LangChain4jException("Unknown host '" + host + "'", e);
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress.test(address)) {
                throw new InvalidRequestException("Host '" + host + "' resolves to a non-public address."
                        + " If you trust it, add it to allowNonPublicHosts of the DefaultMediaFetcher");
            }
        }
        return addresses;
    }

    private BodySubscriber<byte[]> bodySubscriber(ResponseInfo response) {
        if (!isSuccessful(response.statusCode())) {
            return LimitedBodySubscriber.discarding();
        }
        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        if (contentLength.isPresent() && contentLength.getAsLong() > maxSizeInBytes) {
            return LimitedBodySubscriber.failing(tooLarge());
        }
        return LimitedBodySubscriber.collecting(maxSizeInBytes, this::tooLarge);
    }

    private InvalidRequestException tooLarge() {
        return new InvalidRequestException("The media is larger than maxSizeInBytes (" + maxSizeInBytes + " bytes)");
    }

    private static boolean isSuccessful(int status) {
        return status >= 200 && status <= 299;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private URI redirectTarget(URI current, String host, HttpResponse<?> response, int redirectsSoFar) {
        if (redirectsSoFar >= maxRedirects) {
            throw new InvalidRequestException("Host '" + host + "' answered with a redirect (HTTP status "
                    + response.statusCode() + "), but maxRedirects (" + maxRedirects + ") is reached");
        }
        String location = response.headers()
                .firstValue("Location")
                .orElseThrow(() -> new InvalidRequestException("Host '" + host
                        + "' answered with a redirect (HTTP status " + response.statusCode()
                        + ") without a Location header"));
        URI target;
        try {
            // a URI without a path (http://host) resolves a relative reference without the separating slash
            URI base = current.getRawPath() == null || current.getRawPath().isEmpty() ? current.resolve("/") : current;
            target = base.resolve(new URI(location));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new InvalidRequestException("Host '" + host + "' answered with a redirect to an invalid URL", e);
        }
        if ("https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(target.getScheme())) {
            throw new InvalidRequestException(
                    "Host '" + host + "' answered with a redirect from https to http, which is not allowed");
        }
        return target;
    }

    private static @Nullable String mimeType(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(contentType -> {
                    int parameters = contentType.indexOf(';');
                    String mimeType = parameters < 0 ? contentType : contentType.substring(0, parameters);
                    return mimeType.trim().toLowerCase(Locale.ROOT);
                })
                .filter(mimeType -> !mimeType.isEmpty())
                .orElse(null);
    }

    private RuntimeException toFetchException(Throwable error, @Nullable String host) {
        Throwable cause = unwrapCompletionException(error);
        for (Throwable t = cause; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof LangChain4jException langChain4jException) {
                return langChain4jException;
            }
        }
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return timedOut(host, cause);
        }
        return new LangChain4jException("Failed to fetch media from host '" + host + "'", cause);
    }

    private LangChain4jException timedOut(@Nullable String host, @Nullable Throwable cause) {
        return new LangChain4jException(
                "Fetching media from host '" + host + "' did not complete within the timeout (" + timeout + ")", cause);
    }

    private Executor executor() {
        ExecutorService result = executor;
        if (result == null) {
            synchronized (lock) {
                result = executor;
                if (result == null) {
                    result = Executors.newCachedThreadPool(runnable -> {
                        Thread thread = new Thread(runnable, "langchain4j-media-fetcher-" + THREAD_COUNTER.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
                    executor = result;
                }
            }
        }
        return result;
    }

    HttpClient httpClient() {
        HttpClient result = httpClient;
        if (result == null) {
            synchronized (lock) {
                result = httpClient;
                if (result == null) {
                    result = createHttpClient();
                    httpClient = result;
                }
            }
        }
        return result;
    }

    boolean isHttpClientCreated() {
        return httpClient != null;
    }

    private HttpClient createHttpClient() {
        if (httpClientBuilder == null) {
            return HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(connectTimeout)
                    .proxy(new PinningProxySelector())
                    .executor(executor())
                    .build();
        }
        httpClientBuilder.followRedirects(HttpClient.Redirect.NEVER).connectTimeout(connectTimeout);
        HttpClient client = httpClientBuilder.build();
        if (client.proxy().filter(proxy -> !(proxy instanceof PinningProxySelector)).isPresent()) {
            return client;
        }
        // HttpClient.Builder cannot tell whether it has a proxy, only the built client can. Without a proxy of the user,
        // a second client with the pinning proxy selector is built, and the first one is closed (on Java 21 and later).
        if (client instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        return httpClientBuilder.proxy(new PinningProxySelector()).build();
    }

    /**
     * Makes the JDK client send each plain {@code http} request to an address that was checked: the client asks it for
     * a proxy, and it returns the checked address of the host as an HTTP proxy, so the client connects to exactly that
     * address and sends the request in absolute form ({@code GET http://host/path}), which every HTTP/1.1 server
     * accepts. It also keeps the JVM-wide proxy settings from being used.
     */
    private final class PinningProxySelector extends ProxySelector {

        @Override
        public List<Proxy> select(URI uri) {
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || allowNonPublicHosts.matches(HostPatterns.normalize(host))) {
                return List.of(Proxy.NO_PROXY);
            }
            InetAddress address = publicAddresses(host)[0];
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(address, port)));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException e) {}
    }

    /**
     * The state of one download: the result, the request in flight, and the timeout for the whole download.
     */
    private final class Fetch {

        private final CompletableFuture<FetchedMedia> result = new CompletableFuture<>();
        private volatile @Nullable CompletableFuture<?> inFlight;
        private volatile @Nullable String host;

        private Fetch(URI url) {
            this.host = url.getHost();
            CompletableFuture<Void> timer = new CompletableFuture<Void>().orTimeout(timeout.toNanos(), NANOSECONDS);
            timer.whenCompleteAsync(
                    (ignored, error) -> {
                        if (error != null) {
                            fail(timedOut(host, null));
                        }
                    },
                    executor());
            result.whenComplete((media, error) -> {
                timer.complete(null);
                CompletableFuture<?> request = inFlight;
                if (request != null) {
                    request.cancel(true);
                }
            });
        }

        private void inFlight(CompletableFuture<?> request) {
            inFlight = request;
            if (result.isDone()) {
                request.cancel(true);
            }
        }

        private void fail(Throwable error) {
            restoreInterruptIfInterrupted(error);
            result.completeExceptionally(error);
        }
    }

    @FunctionalInterface
    interface HostResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /**
     * Builder of a {@link DefaultMediaFetcher}. Every option is optional; {@code null} means the default.
     */
    @Experimental
    public static final class Builder {

        private @Nullable Collection<String> allowedHosts;
        private @Nullable Collection<String> allowNonPublicHosts;
        private @Nullable Duration connectTimeout;
        private @Nullable Duration timeout;
        private @Nullable Integer maxSizeInBytes;
        private @Nullable Integer maxRedirects;
        private HttpClient.@Nullable Builder httpClientBuilder;

        private Builder() {}

        /**
         * Sets the only hosts that media can be downloaded from. Each entry is a host name such as
         * {@code "images.example.com"}, or {@code "*.example.com"} for every subdomain of {@code example.com} (but not
         * {@code example.com} itself). Case does not matter. The list also applies to every URL that a redirect leads
         * to.
         * <p>
         * When the media URLs can come from end users or models, setting this list is the best protection: the
         * address check cannot tell an internal service that has a public address from any other public server.
         * The hosts in the list must still resolve to public addresses, unless they are in
         * {@link #allowNonPublicHosts(Collection)} as well.
         * <p>
         * Default: {@code null}, which allows every host. An empty list is rejected by {@link #build()}, because it is
         * not clear whether it should allow every host or none.
         *
         * @param allowedHosts the allowed hosts, or {@code null} to allow every host.
         * @return {@code this}.
         */
        public Builder allowedHosts(@Nullable Collection<String> allowedHosts) {
            this.allowedHosts = allowedHosts;
            return this;
        }

        /**
         * Sets the only hosts that media can be downloaded from. See {@link #allowedHosts(Collection)}.
         *
         * @param allowedHosts the allowed hosts, or {@code null} to allow every host.
         * @return {@code this}.
         */
        public Builder allowedHosts(String @Nullable ... allowedHosts) {
            return allowedHosts(allowedHosts == null ? null : Arrays.asList(allowedHosts));
        }

        /**
         * Sets the hosts that may resolve to non-public addresses, for example {@code "localhost"}, {@code "minio"} in
         * a Docker Compose network, or {@code "*.intranet.example.com"}. The entries have the same format as in
         * {@link #allowedHosts(Collection)}; an IP address can be listed as written in the URL, for example
         * {@code "192.168.1.10"}.
         * <p>
         * The addresses of these hosts are not checked at all, so list only hosts you trust. Every other host must
         * still resolve to public addresses only. When {@link #allowedHosts(Collection)} is set as well, a host must
         * be in both lists.
         * <p>
         * Default: none.
         *
         * @param allowNonPublicHosts the hosts that may resolve to non-public addresses, or {@code null} for none.
         * @return {@code this}.
         */
        public Builder allowNonPublicHosts(@Nullable Collection<String> allowNonPublicHosts) {
            this.allowNonPublicHosts = allowNonPublicHosts;
            return this;
        }

        /**
         * Sets the hosts that may resolve to non-public addresses. See {@link #allowNonPublicHosts(Collection)}.
         *
         * @param allowNonPublicHosts the hosts that may resolve to non-public addresses, or {@code null} for none.
         * @return {@code this}.
         */
        public Builder allowNonPublicHosts(String @Nullable ... allowNonPublicHosts) {
            return allowNonPublicHosts(allowNonPublicHosts == null ? null : Arrays.asList(allowNonPublicHosts));
        }

        /**
         * Sets the time that connecting to a host can take. It is also set on the
         * {@link #httpClientBuilder(HttpClient.Builder)}.
         * <p>
         * Default: 10 seconds.
         *
         * @param connectTimeout the connect timeout, or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder connectTimeout(@Nullable Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the time that a whole download can take: looking up the host names, connecting, following the
         * redirects and reading the content. A slower download fails with a {@link LangChain4jException}.
         * <p>
         * Default: 60 seconds.
         *
         * @param timeout the timeout of the whole download, or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum size of the downloaded content in bytes. Larger content fails with an
         * {@link InvalidRequestException}, without being read to the end.
         * <p>
         * Default: 20 MiB (20 * 1024 * 1024 bytes).
         *
         * @param maxSizeInBytes the maximum size in bytes, or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder maxSizeInBytes(@Nullable Integer maxSizeInBytes) {
            this.maxSizeInBytes = maxSizeInBytes;
            return this;
        }

        /**
         * Sets how many redirects are followed. A further redirect fails with an {@link InvalidRequestException};
         * with {@code 0}, every redirect does.
         * <p>
         * Default: 5.
         *
         * @param maxRedirects the maximum number of redirects, or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder maxRedirects(@Nullable Integer maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        /**
         * Sets the builder of the JDK {@link HttpClient} that downloads the media, for example to set a proxy, an
         * {@link SSLContext} or an executor. The fetcher always sets on it {@link HttpClient.Redirect#NEVER}, because
         * it follows redirects itself to check every URL, and the {@link #connectTimeout(Duration)}.
         * <p>
         * If the builder has no proxy, the fetcher sets its own {@link ProxySelector}, which sends each plain
         * {@code http} request to the address that was checked, and the JVM-wide proxy settings are not used.
         * If the builder has a proxy, for example because your network reaches the internet only through one, the
         * fetcher uses it. The proxy then looks up the host names and connects, so the address check only shows what
         * the host names resolve to on this machine, not where the proxy connects. In that case, setting
         * {@link #allowedHosts(Collection)} is even more important.
         * <p>
         * Cookies and credentials are sent only if you set a cookie handler or an authenticator on the builder.
         * <p>
         * Default: a builder for HTTP/1.1 with no other settings.
         *
         * @param httpClientBuilder the builder of the JDK HTTP client, or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder httpClientBuilder(HttpClient.@Nullable Builder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Builds the fetcher. No {@link HttpClient} is created yet: that happens on the first download.
         *
         * @return the fetcher.
         * @throws IllegalArgumentException if an option is invalid, for example an empty
         *                                  {@link #allowedHosts(Collection)} or a negative
         *                                  {@link #maxRedirects(Integer)}.
         */
        public DefaultMediaFetcher build() {
            return new DefaultMediaFetcher(this);
        }
    }
}
