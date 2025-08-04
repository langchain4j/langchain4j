package dev.langchain4j.data.document.source;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
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
    private int connectTimeoutMillis;
    private int readTimeoutMillis;

    private final URL url;

    public UrlSource(URL url) {
        this.url = ensureNotNull(url, "url");
    }

    public UrlSource(URL url, int connectTimeoutMillis, int readTimeoutMillis) {
        this.url = ensureNotNull(url, "url");
        this.connectTimeoutMillis = ensureBetween(connectTimeoutMillis, 1, Integer.MAX_VALUE, "connectTimeoutMillis");
        this.readTimeoutMillis = ensureBetween(readTimeoutMillis, 1, Integer.MAX_VALUE, "readTimeoutMillis");
    }

    @Override
    public InputStream inputStream() throws IOException {
        URLConnection connection = url.openConnection();
        if (connectTimeoutMillis > 0) {
            connection.setConnectTimeout(connectTimeoutMillis);
        }

        if (readTimeoutMillis > 0) {
            connection.setReadTimeout(readTimeoutMillis);
        }

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
}
