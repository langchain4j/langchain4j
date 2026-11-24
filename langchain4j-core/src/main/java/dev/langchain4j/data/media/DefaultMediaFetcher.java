package dev.langchain4j.data.media;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.restoreInterruptIfInterrupted;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.spi.ExecutorProvider;
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
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;

/**
 * The {@link MediaFetcher} that LangChain4j uses unless another one is configured. It downloads media (an image, audio,
 * video or PDF file) from a URL with the JDK {@link HttpClient}. Media URLs can come from end users, from documents, or
 * from tools that a model calls, so by default it downloads only from public hosts, with these rules:
 * <ul>
 *     <li>Only {@code http} and {@code https} URLs. Local files ({@code file:} URLs and plain paths) and all other
 *     schemes are rejected. To send a local file, read it yourself, for example with
 *     {@link ImageContent#from(Path, String)} or {@link PdfFileContent#from(Path)}.</li>
 *     <li>Only hosts whose addresses are all public. Loopback, private, link-local, multicast and other special-purpose
 *     addresses are rejected, also when they are written in another form or reached through a redirect. To download
 *     from your own servers, list them in {@link Builder#allowNonPublicHosts(String...)}.</li>
 *     <li>If you set {@link Builder#allowedHosts(String...)}, only these hosts.</li>
 *     <li>Redirects are followed up to {@link Builder#maxRedirects(Integer)} times, and every URL they lead to is
 *     checked again. A redirect from {@code https} to {@code http} is rejected.</li>
 *     <li>One {@link Builder#timeout(Duration)} for the whole download, and a size limit
 *     ({@link Builder#maxSizeInBytes(Integer)}) for each download. A request with several media URLs downloads each
 *     of them, each up to this size.</li>
 *     <li>No cookies and no credentials are sent, compressed content is not requested, and the JVM-wide proxy settings
 *     are not used (see {@link Builder#httpClientBuilder(HttpClient.Builder)}). With a proxy, host names that only the
 *     proxy can resolve are passed to the proxy unchecked.</li>
 * </ul>
 * For example, a fetcher that downloads only from your CDN, up to 5 MiB:
 * <pre>{@code
 * MediaFetcher mediaFetcher = DefaultMediaFetcher.builder()
 *         .allowedHosts("images.example.com", "*.cdn.example.com")
 *         .maxSizeInBytes(5 * 1024 * 1024)
 *         .build();
 *
 * FetchedMedia media = mediaFetcher.fetch(MediaFetchRequest.from(URI.create("https://images.example.com/cat.png")));
 * }</pre>
 * A model integration that downloads media has a {@code mediaFetcher(...)} option on its builder (and on the builders
 * of its token count estimator and cache, if it has them), where you set such a fetcher.
 * <p>
 * How addresses are checked: the fetcher resolves the host name itself and checks every address it gets. A plain
 * {@code http} request is then sent to exactly the address that was checked, so a DNS answer that changes between the
 * check and the connection has no effect. A public address can still belong to a service that trusts requests from
 * your servers: when the URLs come from end users or models, set {@link Builder#allowedHosts(String...)} where you
 * can, which is the strictest setting.
 * <p>
 * What the checks do not cover:
 * <ul>
 *     <li>For {@code https}, the HTTP client resolves the host name again when it connects, and relies on certificate
 *     and host name verification: a server reached through a changed DNS answer cannot complete the connection
 *     without a valid certificate for the host name.</li>
 *     <li>{@link Builder#allowNonPublicHosts(String...)} allows every port of a listed host.</li>
 *     <li>An authenticator or a cookie handler set on the {@link Builder#httpClientBuilder(HttpClient.Builder)}
 *     answers every host that the fetcher contacts, including the hosts that redirects lead to.</li>
 * </ul>
 * An instance is thread-safe and is meant to be shared. It has no threads of its own:
 * {@link #fetch(MediaFetchRequest)} looks up the host names, checks them and waits for the download on the calling
 * thread, and {@link #fetchAsync(MediaFetchRequest)}, which never blocks the calling thread, does this work on the
 * default executor of LangChain4j, which an application can replace with an {@link ExecutorProvider}. The
 * {@link HttpClient} is created for the first request that the fetcher sends, and runs its own work on the executor
 * that the JDK creates for each client. There is nothing to close: the {@link HttpClient} and its threads end after a garbage
 * collection once nothing references the fetcher any more and no download is in progress. As long as a model or your
 * code holds the fetcher, its {@link HttpClient} and the threads of the client stay alive.
 *
 * @since 1.25.0
 */
@Experimental
public final class DefaultMediaFetcher implements MediaFetcher {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration MAX_TIMEOUT = Duration.ofNanos(Long.MAX_VALUE);
    private static final int DEFAULT_MAX_SIZE_IN_BYTES = 50 * 1024 * 1024;
    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "LangChain4j";
    private static final String ON_THE_MODEL_BUILDER =
            " on the model builder (on each model, token count estimator or cache that sees this URL)";

    private final @Nullable HostPatterns allowedHosts;
    private final AddressCheck addressCheck;
    private final Duration connectTimeout;
    private final boolean connectTimeoutSet;
    private final Duration timeout;
    private final int maxSizeInBytes;
    private final int maxRedirects;
    private final HttpClient.@Nullable Builder httpClientBuilder;

    private final Object lock = new Object();
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
        HostPatterns allowNonPublicHosts = builder.allowNonPublicHosts == null
                ? HostPatterns.none()
                : HostPatterns.from(builder.allowNonPublicHosts, "allowNonPublicHosts");
        this.addressCheck = new AddressCheck(allowNonPublicHosts, hostResolver, isPublicAddress);
        this.connectTimeout =
                ensureValidTimeout(getOrDefault(builder.connectTimeout, DEFAULT_CONNECT_TIMEOUT), "connectTimeout");
        this.connectTimeoutSet = builder.connectTimeout != null;
        this.timeout = ensureValidTimeout(getOrDefault(builder.timeout, DEFAULT_TIMEOUT), "timeout");
        this.maxSizeInBytes =
                ensureGreaterThanZero(getOrDefault(builder.maxSizeInBytes, DEFAULT_MAX_SIZE_IN_BYTES), "maxSizeInBytes");
        this.maxRedirects = ensureNotNegative(getOrDefault(builder.maxRedirects, DEFAULT_MAX_REDIRECTS), "maxRedirects");
        this.httpClientBuilder = builder.httpClientBuilder;
    }

    private static Duration ensureValidTimeout(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw illegalArgument("%s must be positive, but is: %s", name, duration);
        }
        if (duration.compareTo(MAX_TIMEOUT) > 0) {
            throw illegalArgument(
                    "%s must not be longer than %s (about 292 years), but is: %s", name, MAX_TIMEOUT, duration);
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
     * Downloads the media at the URL of the given request, checking it and every URL that a redirect leads to as
     * described in the class documentation.
     * <p>
     * The host names are looked up on the calling thread. A slow host lookup is not cut short by
     * {@link Builder#timeout(Duration)}, but the time it takes counts towards it ({@link #fetchAsync(MediaFetchRequest)}
     * cuts it short).
     *
     * @param request the request with the URL of the media, for example {@code https://example.com/cat.png}.
     * @return the downloaded media, with the MIME type from the {@code Content-Type} header of the response, never
     *         {@code null}.
     * @throws InvalidRequestException if the URL or a URL that a redirect leads to is not allowed, if the content is
     *                                 larger than {@link Builder#maxSizeInBytes(Integer)}, if there are more than
     *                                 {@link Builder#maxRedirects(Integer)} redirects or an invalid redirect, or if
     *                                 the host answers with an HTTP status that is not successful (other than 408,
     *                                 429 and 5xx).
     * @throws TimeoutException        if connecting takes longer than {@link Builder#connectTimeout(Duration)}, if the
     *                                 download does not complete within {@link Builder#timeout(Duration)}, or if the
     *                                 host answers with HTTP status 408.
     * @throws RateLimitException      if the host answers with HTTP status 429.
     * @throws InternalServerException if the host answers with an HTTP status 5xx.
     * @throws LangChain4jException    if the download fails for another reason, for example because the host is unknown
     *                                 or cannot be reached.
     */
    @Override
    public FetchedMedia fetch(MediaFetchRequest request) {
        ensureNotNull(request, "request");
        URI url = request.url();
        long start = System.nanoTime();
        String host = url.getHost();
        try {
            for (int redirects = 0; ; redirects++) {
                host = checkUrl(url);
                HttpResponse<byte[]> response = send(url, host, start);
                if (!isRedirect(response.statusCode())) {
                    return media(host, response);
                }
                url = redirectTarget(url, host, response, redirects);
            }
        } catch (RuntimeException e) {
            throw toFetchException(e, host);
        }
    }

    private HttpResponse<byte[]> send(URI url, String host, long start) {
        long remainingNanos = timeout.toNanos() - (System.nanoTime() - start);
        if (remainingNanos <= 0) {
            throw timedOut(host, null);
        }
        CompletableFuture<HttpResponse<byte[]>> response =
                httpClient().sendAsync(httpRequest(url), bodyHandler(host, maxSizeInBytes));
        try {
            return response.get(remainingNanos, NANOSECONDS);
        } catch (InterruptedException e) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new LangChain4jException("Interrupted while fetching media", e);
        } catch (java.util.concurrent.TimeoutException e) {
            response.cancel(true);
            throw timedOut(host, null);
        } catch (ExecutionException e) {
            throw toFetchException(e.getCause(), host);
        }
    }

    /**
     * Downloads the media at the URL of the given request like {@link #fetch(MediaFetchRequest)}, without blocking
     * the calling thread: the host lookups and the checks run on the default executor of LangChain4j (see
     * {@link ExecutorProvider}), and {@link Builder#timeout(Duration)} also limits the host lookups. Cancelling the
     * returned future stops the download.
     *
     * @param request the request with the URL of the media, for example {@code https://example.com/cat.png}.
     * @return a future of the downloaded media. It fails with the exceptions described in
     *         {@link #fetch(MediaFetchRequest)}.
     */
    @Override
    public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
        try {
            ensureNotNull(request, "request");
            URI url = request.url();
            Fetch fetch = new Fetch(url);
            try {
                DefaultExecutorProvider.getDefaultExecutor().execute(() -> sendRequest(fetch, url, 0));
            } catch (Throwable e) {
                fetch.fail(e);
            }
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
            CompletableFuture<HttpResponse<byte[]>> response =
                    httpClient().sendAsync(httpRequest(url), bodyHandler(host, maxSizeInBytes));
            fetch.inFlight(response);
            response.whenComplete((r, error) -> runOnDefaultExecutor(() -> {
                if (error != null) {
                    fetch.fail(toFetchException(error, host));
                } else {
                    handleResponse(fetch, url, host, redirects, r);
                }
            }));
        } catch (Throwable e) {
            fetch.fail(e instanceof Error ? e : toFetchException(e, fetch.host));
        }
    }

    private void handleResponse(Fetch fetch, URI url, String host, int redirects, HttpResponse<byte[]> response) {
        try {
            if (isRedirect(response.statusCode())) {
                sendRequest(fetch, redirectTarget(url, host, response, redirects), redirects + 1);
            } else {
                fetch.result.complete(media(host, response));
            }
        } catch (Throwable e) {
            fetch.fail(e instanceof Error ? e : toFetchException(e, host));
        }
    }

    /**
     * Runs the task on the default executor of LangChain4j, which is resolved again for each task, so that an
     * {@link ExecutorProvider} registered later is used too. If that executor rejects the task, for example because
     * it is bounded and busy, the task runs on the calling thread, so that a download always completes.
     */
    private static void runOnDefaultExecutor(Runnable task) {
        try {
            DefaultExecutorProvider.getDefaultExecutor().execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    private static HttpRequest httpRequest(URI url) {
        return HttpRequest.newBuilder(requestUri(url))
                .GET()
                .header("User-Agent", USER_AGENT)
                .build();
    }

    private static FetchedMedia media(String host, HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (!isSuccessful(status)) {
            throw notSuccessful(host, status);
        }
        return FetchedMedia.from(response.body(), mimeType(response));
    }

    /**
     * Returns the exception for an HTTP status that is not successful: a {@link RetriableException} for a temporary
     * failure that may succeed when retried, and an {@link InvalidRequestException} otherwise.
     */
    private static LangChain4jException notSuccessful(String host, int status) {
        String message = "Host '" + host + "' answered with HTTP status " + status;
        if (status == 408) {
            return new TimeoutException(message);
        }
        if (status == 429) {
            return new RateLimitException(message);
        }
        if (status >= 500 && status <= 599) {
            return new InternalServerException(message);
        }
        return new InvalidRequestException(message);
    }

    private String checkUrl(URI url) {
        String scheme = url.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidRequestException(unsupportedScheme(scheme));
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new InvalidRequestException("The media URL has no valid host");
        }
        if (allowedHosts != null && !allowedHosts.matches(HostPatterns.normalize(host))) {
            throw new InvalidRequestException("Host '" + host + "' is not in allowedHosts of the DefaultMediaFetcher."
                    + " To allow it, add it to allowedHosts: set"
                    + " mediaFetcher(DefaultMediaFetcher.builder().allowedHosts(..., \"" + host + "\").build())"
                    + ON_THE_MODEL_BUILDER);
        }
        addressCheck.checkAddresses(host, () -> sentThroughProxy(url));
        return host;
    }

    /**
     * Returns whether the request for the given URL goes through an HTTP proxy of the
     * {@link Builder#httpClientBuilder(HttpClient.Builder)}.
     */
    private boolean sentThroughProxy(URI url) {
        if (httpClientBuilder == null) {
            return false;
        }
        return httpClient().proxy().orElse(null) instanceof PinningProxySelector pinningProxySelector
                && pinningProxySelector.sendsThroughProxy(requestUri(url));
    }

    private static String unsupportedScheme(@Nullable String scheme) {
        String message = "Only http and https URLs can be fetched, but got "
                + (scheme == null ? "a URL without a scheme" : "a URL with the scheme '" + scheme + "'");
        // a scheme of one letter is the drive of a Windows path, such as C:/images/cat.png
        if (scheme == null || scheme.length() == 1 || scheme.equalsIgnoreCase("file")) {
            return message + ". Local files are not read from URLs: create the content from the file instead, for"
                    + " example with ImageContent.from(Path, String), PdfFileContent.from(Path),"
                    + " AudioContent.from(Path, String) or VideoContent.from(Path, String), or for an image model with"
                    + " Image.builder().base64Data(...)";
        }
        if (scheme.equalsIgnoreCase("data")) {
            return message + ". Pass the base64 data (the part after \"base64,\") to the base64 factory of the content"
                    + " instead: ImageContent.from(String base64Data, String mimeType),"
                    + " PdfFileContent.from(String base64Data, String mimeType),"
                    + " AudioContent.from(String base64Data, String mimeType) or"
                    + " VideoContent.from(String base64Data, String mimeType)";
        }
        return message;
    }

    /**
     * Returns the URL without its user info and fragment, which are not meant to be sent: the JDK client sends the
     * whole URL in the request line when it sends a request through a proxy, as it does for each plain {@code http}
     * request (see {@link PinningProxySelector}).
     */
    private static URI requestUri(URI url) {
        String path = isNullOrEmpty(url.getRawPath()) ? "/" : url.getRawPath();
        String query = url.getRawQuery() == null ? "" : "?" + url.getRawQuery();
        String port = url.getPort() == -1 ? "" : ":" + url.getPort();
        URI requestUri = URI.create(url.getScheme() + "://" + url.getHost() + port + path + query);
        return URI.create(requestUri.toASCIIString());
    }

    private static BodyHandler<byte[]> bodyHandler(String host, int maxSizeInBytes) {
        return response -> {
            if (!isSuccessful(response.statusCode())) {
                return LimitedBodySubscriber.discarding();
            }
            OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
            if (contentLength.isPresent() && contentLength.getAsLong() > maxSizeInBytes) {
                return LimitedBodySubscriber.failing(tooLarge(host, maxSizeInBytes));
            }
            return LimitedBodySubscriber.collecting(
                    maxSizeInBytes, contentLength, () -> tooLarge(host, maxSizeInBytes));
        };
    }

    private static InvalidRequestException tooLarge(String host, int maxSizeInBytes) {
        return new InvalidRequestException("The media from host '" + host + "' is larger than maxSizeInBytes ("
                + maxSizeInBytes + " bytes). To allow larger media, set"
                + " mediaFetcher(DefaultMediaFetcher.builder().maxSizeInBytes(...).build())" + ON_THE_MODEL_BUILDER);
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
                    + response.statusCode() + "), but maxRedirects (" + maxRedirects + ") is reached. To follow more"
                    + " redirects, set mediaFetcher(DefaultMediaFetcher.builder().maxRedirects(...).build())"
                    + ON_THE_MODEL_BUILDER);
        }
        String location = response.headers()
                .firstValue("Location")
                .orElseThrow(() -> new InvalidRequestException("Host '" + host
                        + "' answered with a redirect (HTTP status " + response.statusCode()
                        + ") without a Location header"));
        URI target;
        try {
            target = resolve(current, new URI(location));
        } catch (URISyntaxException e) {
            // without the URL and the exception, whose message contains it: it can contain credentials
            throw new InvalidRequestException(
                    "Host '" + host + "' answered with a redirect to an invalid URL (" + e.getReason() + ")");
        }
        if ("https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(target.getScheme())) {
            throw new InvalidRequestException(
                    "Host '" + host + "' answered with a redirect from https to http, which is not allowed");
        }
        return target;
    }

    private static URI resolve(URI base, URI reference) throws URISyntaxException {
        String basePath = isNullOrEmpty(base.getRawPath()) ? "/" : base.getRawPath();
        if (reference.getScheme() == null
                && reference.getRawAuthority() == null
                && isNullOrEmpty(reference.getRawPath())) {
            // RFC 3986 keeps the path for a reference such as "?page=2", but URI.resolve follows RFC 2396, which drops
            // the last segment of the path
            String query = reference.getRawQuery() == null ? base.getRawQuery() : reference.getRawQuery();
            return new URI(base.getScheme() + "://" + base.getRawAuthority() + basePath
                    + (query == null ? "" : "?" + query));
        }
        // a URI without a path (http://host) resolves a relative reference without the separating slash
        return (isNullOrEmpty(base.getRawPath()) ? base.resolve("/") : base).resolve(reference);
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
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof LangChain4jException langChain4jException) {
                return langChain4jException;
            }
        }
        if (cause instanceof HttpConnectTimeoutException) {
            return new TimeoutException(
                    "Could not connect to host '" + host + "' within connectTimeout ("
                            + httpClient().connectTimeout().map(String::valueOf).orElse("not set") + ")",
                    cause);
        }
        if (cause instanceof HttpTimeoutException) {
            return timedOut(host, cause);
        }
        return new LangChain4jException("Failed to fetch media from host '" + host + "'", cause);
    }

    private TimeoutException timedOut(@Nullable String host, @Nullable Throwable cause) {
        return new TimeoutException(
                "Fetching media from host '" + host + "' did not complete within the timeout (" + timeout + ")", cause);
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
                    .proxy(new PinningProxySelector(addressCheck, null))
                    .build();
        }
        httpClientBuilder.followRedirects(HttpClient.Redirect.NEVER);
        if (connectTimeoutSet) {
            httpClientBuilder.connectTimeout(connectTimeout);
        }
        ProxySelector proxySelector = proxySelectorOf(httpClientBuilder);
        return httpClientBuilder
                .proxy(new PinningProxySelector(addressCheck, proxySelector))
                .build();
    }

    /**
     * Returns the proxy selector of the given builder. {@link HttpClient.Builder} cannot tell whether it has one, only
     * a client built from it can, so a client is built and closed (on Java 21 and later; on older versions, it ends
     * once it is garbage collected).
     */
    private static @Nullable ProxySelector proxySelectorOf(HttpClient.Builder httpClientBuilder) {
        HttpClient client = httpClientBuilder.build();
        ProxySelector proxySelector = client.proxy().orElse(null);
        if (client instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
        if (proxySelector instanceof PinningProxySelector pinningProxySelector) {
            return pinningProxySelector.proxySelector; // the builder was given to another fetcher before
        }
        return proxySelector;
    }

    /**
     * Checks that a host resolves to public addresses only, unless it is in allowNonPublicHosts.
     */
    private static final class AddressCheck {

        private final HostPatterns allowNonPublicHosts;
        private final HostResolver hostResolver;
        private final Predicate<InetAddress> isPublicAddress;

        private AddressCheck(
                HostPatterns allowNonPublicHosts, HostResolver hostResolver, Predicate<InetAddress> isPublicAddress) {
            this.allowNonPublicHosts = allowNonPublicHosts;
            this.hostResolver = hostResolver;
            this.isPublicAddress = isPublicAddress;
        }

        private boolean mayBeNonPublic(String host) {
            return allowNonPublicHosts.matches(HostPatterns.normalize(host));
        }

        /**
         * Checks the addresses of the host. A host name that cannot be resolved here is not checked if
         * {@code sentThroughProxy} is true: the proxy resolves it and connects itself.
         */
        private void checkAddresses(String host, BooleanSupplier sentThroughProxy) {
            if (!mayBeNonPublic(host)) {
                publicAddresses(host, sentThroughProxy);
            }
        }

        private InetAddress[] publicAddresses(String host, BooleanSupplier sentThroughProxy) {
            InetAddress[] addresses;
            try {
                addresses = hostResolver.resolve(host);
            } catch (UnknownHostException e) {
                if (!isIpAddress(host) && sentThroughProxy.getAsBoolean()) {
                    return new InetAddress[0];
                }
                throw new LangChain4jException("Unknown host '" + host + "'", e);
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress.test(address)) {
                    throw new InvalidRequestException("Host '" + host + "' resolves to a non-public address. If you"
                            + " trust it, add it to allowNonPublicHosts: set"
                            + " mediaFetcher(DefaultMediaFetcher.builder().allowNonPublicHosts(\"" + host
                            + "\").build())" + ON_THE_MODEL_BUILDER);
                }
            }
            return addresses;
        }

        /**
         * Returns whether the host is an IP address in any notation, also one that the JDK does not read as an
         * address but a proxy may, such as {@code 0x7f.1}: the last label of a host name is never a number.
         */
        private static boolean isIpAddress(String host) {
            if (host.startsWith("[")) {
                return true;
            }
            String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
            String lastLabel = name.substring(name.lastIndexOf('.') + 1);
            return lastLabel.matches("[0-9]+|0[xX][0-9a-fA-F]*");
        }
    }

    /**
     * Makes the JDK client send each plain {@code http} request to an address that was checked: the client asks it for
     * a proxy, and it returns the checked address of the host as an HTTP proxy, so the client connects to exactly that
     * address and sends the request in absolute form ({@code GET http://host/path}), which every HTTP/1.1 server
     * accepts.
     * <p>
     * It wraps the proxy selector of the {@link Builder#httpClientBuilder(HttpClient.Builder)}, if there is one: a
     * request that this selector sends through an HTTP proxy goes there, and the others are pinned. Without one, the
     * JVM-wide proxy settings are not used.
     * <p>
     * The client keeps it as long as the client exists, so it must not refer to the fetcher: that would keep the client
     * and its thread alive after the fetcher is no longer used.
     */
    private static final class PinningProxySelector extends ProxySelector {

        private final AddressCheck addressCheck;
        private final @Nullable ProxySelector proxySelector;

        private PinningProxySelector(AddressCheck addressCheck, @Nullable ProxySelector proxySelector) {
            this.addressCheck = addressCheck;
            this.proxySelector = proxySelector;
        }

        @Override
        public List<Proxy> select(URI uri) {
            List<Proxy> proxies = proxySelector == null ? List.of(Proxy.NO_PROXY) : proxySelector.select(uri);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || addressCheck.mayBeNonPublic(host)
                    || isHttpProxy(proxies)) {
                return proxies;
            }
            InetAddress address = addressCheck.publicAddresses(host, () -> false)[0];
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(address, port)));
        }

        /**
         * Returns whether the proxy selector of the {@link Builder#httpClientBuilder(HttpClient.Builder)} sends the
         * request for the given URI through an HTTP proxy.
         */
        private boolean sendsThroughProxy(URI uri) {
            return proxySelector != null && isHttpProxy(proxySelector.select(uri));
        }

        private static boolean isHttpProxy(@Nullable List<Proxy> proxies) {
            // the JDK client uses only the first proxy, and only an HTTP proxy; otherwise it connects directly
            return proxies != null && !proxies.isEmpty() && proxies.get(0).type() == Proxy.Type.HTTP;
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException e) {
            if (proxySelector != null) {
                proxySelector.connectFailed(uri, address, e);
            }
        }
    }

    /**
     * The state of one download of {@link #fetchAsync(MediaFetchRequest)}: the result, the request in flight, and the
     * timeout for the whole download. The timeout fires on the delay scheduler of the JDK, so it does not need a free
     * thread of the default executor; the result is then completed on the default executor.
     */
    private final class Fetch {

        private final CompletableFuture<FetchedMedia> result = new CompletableFuture<>();
        private volatile @Nullable CompletableFuture<?> inFlight;
        private volatile @Nullable String host;

        private Fetch(URI url) {
            this.host = url.getHost();
            CompletableFuture<Void> timer = new CompletableFuture<Void>().orTimeout(timeout.toNanos(), NANOSECONDS);
            timer.whenComplete((ignored, error) -> {
                if (error != null) {
                    runOnDefaultExecutor(() -> fail(timedOut(host, null)));
                }
            });
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
         * {@code example.com} itself), without a scheme, port or path. Case does not matter. A host name with non-ASCII
         * characters, such as <code>b&uuml;cher.example</code>, is converted to its ASCII form
         * ({@code xn--bcher-kva.example}), and media URLs must use that form too. The list also applies to every URL
         * that a redirect leads to.
         * <p>
         * When the media URLs can come from end users or models, setting this list is the strictest setting: the
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
         * Sets the hosts that may resolve to non-public addresses, for example {@code "media.intranet.example.com"},
         * {@code "minio"} in a Docker Compose network, or {@code "*.intranet.example.com"}. The entries have the same
         * format as in {@link #allowedHosts(Collection)}; an IP address can be listed as written in the URL, for
         * example {@code "192.168.1.10"} or {@code "[fd00::5]"}.
         * <p>
         * The addresses of these hosts are not checked at all, and every port of them is allowed, so list only hosts
         * you trust. Every other host must still resolve to public addresses only. When
         * {@link #allowedHosts(Collection)} is set as well, a host must be in both lists.
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
         * Sets the time that connecting to a host can take. A slower connection fails with a
         * {@link TimeoutException}.
         * <p>
         * With an {@link #httpClientBuilder(HttpClient.Builder)}, it is set on that builder only if you set it here;
         * otherwise the connect timeout of that builder applies.
         * <p>
         * Default: 10 seconds.
         *
         * @param connectTimeout the connect timeout, at most about 292 years ({@code Long.MAX_VALUE} nanoseconds), or
         *                       {@code null} for the default.
         * @return {@code this}.
         */
        public Builder connectTimeout(@Nullable Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the time that a whole download can take: looking up the host names, connecting, following the
         * redirects and reading the content. A slower download fails with a {@link TimeoutException}.
         * {@link DefaultMediaFetcher#fetch(MediaFetchRequest)} looks up the host names on the calling thread and
         * cannot cut a slow lookup short, but the time it takes counts towards the timeout.
         * <p>
         * Default: 60 seconds.
         *
         * @param timeout the timeout of the whole download, at most about 292 years ({@code Long.MAX_VALUE}
         *                nanoseconds), or {@code null} for the default.
         * @return {@code this}.
         */
        public Builder timeout(@Nullable Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum size of the downloaded content of each URL in bytes. Larger content fails with an
         * {@link InvalidRequestException}, without being read to the end. The limit applies to each download, so a
         * request with several media URLs downloads each of them, each up to this size.
         * <p>
         * Default: 50 MiB (50 * 1024 * 1024 bytes).
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
         * Sets the builder of the JDK {@link HttpClient} that downloads the media, for example to set a proxy or an
         * {@link SSLContext}.
         * <p>
         * The fetcher changes this builder when it creates its client: it sets {@link HttpClient.Redirect#NEVER},
         * because it follows redirects itself to check every URL, its own {@link ProxySelector}, and the
         * {@link #connectTimeout(Duration)} if you set one. So pass a new builder to each fetcher, and do not reuse or
         * share it.
         * <p>
         * The proxy selector of the fetcher sends each plain {@code http} request to the address that was checked.
         * If the builder has a proxy selector, for example because your network reaches the internet only through a
         * proxy, the requests that this selector sends through an HTTP proxy go to that proxy, and the requests that
         * it sends directly still go to the checked address. A proxy looks up the host names and connects itself, so
         * the address check only shows what the host names resolve to on this machine, not where the proxy connects.
         * In that case, setting {@link #allowedHosts(Collection)} is even more important. With a proxy, host names
         * that only the proxy can resolve are passed to the proxy unchecked: a host name that cannot be resolved on
         * this machine is not rejected when the request goes through an HTTP proxy (IP addresses, and host names that
         * this machine can resolve, are still checked). If the builder has no proxy selector, the JVM-wide proxy
         * settings are not used.
         * <p>
         * An executor set on the builder runs only the work of the {@link HttpClient}: the host lookups and the checks
         * run on the calling thread of {@link DefaultMediaFetcher#fetch(MediaFetchRequest)}, or on the default
         * executor of LangChain4j for {@link DefaultMediaFetcher#fetchAsync(MediaFetchRequest)}.
         * <p>
         * Cookies and credentials are sent only if you set a cookie handler or an authenticator on the builder, and
         * they then answer every host that the fetcher contacts.
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
         *                                  {@link #allowedHosts(Collection)}, a negative
         *                                  {@link #maxRedirects(Integer)} or a {@link #timeout(Duration)} longer than
         *                                  about 292 years.
         */
        public DefaultMediaFetcher build() {
            return new DefaultMediaFetcher(this);
        }
    }
}
