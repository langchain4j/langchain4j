package dev.langchain4j.data.media;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A local HTTP(S) server for the media fetcher tests. It records every request it receives.
 */
final class TestHttpServer implements AutoCloseable {

    private static final String KEY_STORE = "/media-fetcher/localhost.p12";
    private static final char[] KEY_STORE_PASSWORD = "changeit".toCharArray();

    record Request(String method, URI requestUri, Headers headers) {}

    interface Handler {
        void handle(HttpExchange exchange) throws Exception;
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    final List<Request> requests = new CopyOnWriteArrayList<>();
    /** Set when writing the response failed, that is when the client closed the connection early. */
    final AtomicBoolean clientAborted = new AtomicBoolean();

    private TestHttpServer(HttpServer server) {
        this.server = server;
        server.setExecutor(executor);
        server.start();
    }

    static TestHttpServer http(String bindAddress) {
        try {
            return new TestHttpServer(HttpServer.create(new InetSocketAddress(InetAddress.getByName(bindAddress), 0), 0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static TestHttpServer https(String bindAddress) {
        try {
            HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getByName(bindAddress), 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
            return new TestHttpServer(server);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    TestHttpServer on(String path, Handler handler) {
        server.createContext(path, exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(), exchange.getRequestHeaders()));
            try (exchange) {
                handler.handle(exchange);
            } catch (IOException e) {
                clientAborted.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return this;
    }

    int port() {
        return server.getAddress().getPort();
    }

    URI url(String scheme, String host, String path) {
        return URI.create(scheme + "://" + host + ":" + port() + path);
    }

    URI url(String host, String path) {
        return url("http", host, path);
    }

    int hits() {
        return requests.size();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    static Handler ok(byte[] body) {
        return exchange -> {
            exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
        };
    }

    static Handler ok(byte[] body, String contentType) {
        return exchange -> {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            ok(body).handle(exchange);
        };
    }

    static Handler status(int status, String... headerNamesAndValues) {
        return exchange -> {
            for (int i = 0; i < headerNamesAndValues.length; i += 2) {
                exchange.getResponseHeaders().add(headerNamesAndValues[i], headerNamesAndValues[i + 1]);
            }
            exchange.sendResponseHeaders(status, -1);
        };
    }

    static Handler redirect(String location) {
        return redirect(302, location);
    }

    static Handler redirect(int status, String location) {
        return status(status, "Location", location);
    }

    static Handler redirect(URI location) {
        return redirect(location.toString());
    }

    /** Answers with a body of {@code totalBytes} bytes, without a {@code Content-Length} header. */
    static Handler chunked(long totalBytes) {
        return exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            byte[] chunk = new byte[64 * 1024];
            for (long written = 0; written < totalBytes; written += chunk.length) {
                out.write(chunk, 0, (int) Math.min(chunk.length, totalBytes - written));
            }
        };
    }

    /** Sends the headers with the given {@code Content-Length}, then waits up to 10 seconds without sending the body. */
    static Handler headersOnly(long contentLength) {
        return exchange -> {
            exchange.sendResponseHeaders(200, contentLength);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    static Handler delayed(long delayMillis, Handler handler) {
        return exchange -> {
            Thread.sleep(delayMillis);
            handler.handle(exchange);
        };
    }

    /** Sends the headers at once, then one byte every {@code intervalMillis}. */
    static Handler trickle(long intervalMillis, int bytes) {
        return exchange -> {
            exchange.sendResponseHeaders(200, bytes);
            OutputStream out = exchange.getResponseBody();
            for (int i = 0; i < bytes; i++) {
                out.write('x');
                out.flush();
                Thread.sleep(intervalMillis);
            }
        };
    }

    /** Trusts the certificate of {@link #https(String)}, which is valid for {@code localhost} and {@code 127.0.0.1}. */
    static SSLContext clientSslContext() {
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SSLContext serverSslContext() {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore(), KEY_STORE_PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), null, null);
            return context;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = TestHttpServer.class.getResourceAsStream(KEY_STORE)) {
            keyStore.load(in, KEY_STORE_PASSWORD);
        }
        return keyStore;
    }
}
