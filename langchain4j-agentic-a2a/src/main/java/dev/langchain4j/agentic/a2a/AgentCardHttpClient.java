package dev.langchain4j.agentic.a2a;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** Adds a request timeout to the SDK's dedicated agent-card HTTP client. */
final class AgentCardHttpClient extends HttpClient {
    private final HttpClient delegate;
    private final Supplier<Duration> timeout;

    AgentCardHttpClient(Supplier<Duration> timeout) {
        this(HttpClient.newBuilder().followRedirects(Redirect.NEVER).build(), timeout);
    }

    AgentCardHttpClient(HttpClient delegate, Supplier<Duration> timeout) {
        this.delegate = delegate;
        this.timeout = timeout;
    }

    private HttpRequest withTimeout(HttpRequest request) {
        return HttpRequest.newBuilder(request, (name, value) -> true)
                .timeout(timeout.get())
                .build();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        // The JDK request timeout alone does not bound a stalled response body.
        // Time the complete asynchronous exchange, and cancel the actual HTTP future.
        CompletableFuture<HttpResponse<T>> response = delegate.sendAsync(withTimeout(request), handler);
        try {
            return response.get(timeout.get().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new HttpTimeoutException("Agent-card fetch timed out after " + timeout.get());
        } catch (InterruptedException e) {
            response.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException(cause);
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        return delegate.sendAsync(withTimeout(request), handler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
        return delegate.sendAsync(withTimeout(request), handler, pushHandler);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }
}
