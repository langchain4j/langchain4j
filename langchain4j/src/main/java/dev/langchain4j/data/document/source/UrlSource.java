package dev.langchain4j.data.document.source;

import static dev.langchain4j.internal.ValidationUtils.ensureNonNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSource;
import dev.langchain4j.data.document.Metadata;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

public class UrlSource implements DocumentSource {
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    private final URL url;

    public UrlSource(URL url) {
        this(url, 0, 0);
    }

    public UrlSource(URL url, int connectTimeoutMillis, int readTimeoutMillis) {
        this.url = ensureNotNull(url, "url");
        this.connectTimeoutMillis = ensureNonNegative(connectTimeoutMillis, "connectTimeoutMillis");
        this.readTimeoutMillis = ensureNonNegative(readTimeoutMillis, "readTimeoutMillis");
    }

    @Override
    public InputStream inputStream() throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);

        return connection.getInputStream();
    }

    @Override
    public Metadata metadata() {
        return Metadata.from(Document.URL, url.toString());
    }

    public static UrlSource from(String url) {
        try {
            return new UrlSource(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static UrlSource from(URL url) {
        return new UrlSource(url);
    }

    public static UrlSource from(URI uri) {
        try {
            return new UrlSource(uri.toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static UrlSource from(String url, int connectTimeoutMillis, int readTimeoutMillis) {
        try {
            return new UrlSource(new URL(url), connectTimeoutMillis, readTimeoutMillis);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static UrlSource from(URL url, int connectTimeoutMillis, int readTimeoutMillis) {
        return new UrlSource(url, connectTimeoutMillis, readTimeoutMillis);
    }

    public static UrlSource from(URI uri, int connectTimeoutMillis, int readTimeoutMillis) {
        try {
            return new UrlSource(uri.toURL(), connectTimeoutMillis, readTimeoutMillis);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
