package dev.langchain4j.data.document;

import dev.langchain4j.data.document.source.UrlSource;

import java.net.MalformedURLException;
import java.net.URL;

public class WebUrlDocumentLoader {

    /**
     * Loads a document from the specified web URL
     *
     * @param url URL of the web page
     * @return document
     * @throws RuntimeException if specified URL is malformed
     */
    public static Document load(String url) {
        try {
            return load(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a document from the specified web URL.
     *
     * @param url URL of the web page
     * @return document
     */
    public static Document load(URL url) {
        return WebUrlDocumentLoaderUtils.load(UrlSource.from(url));
    }

}
