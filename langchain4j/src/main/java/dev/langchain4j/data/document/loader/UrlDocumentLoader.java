package dev.langchain4j.data.document.loader;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.UrlSource;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlDocumentLoader {

    /**
     * Loads a document from the specified URL.
     *
     * @param url            The URL of the file.
     * @param documentParser The parser to be used for parsing text from the URL.
     * @return document
     */
    public static Document load(URL url, DocumentParser documentParser) {
        return DocumentLoader.load(UrlSource.from(url), documentParser);
    }

    /**
     * Loads a document from the specified URL.
     *
     * @param url            The URL of the file.
     * @param documentParser The parser to be used for parsing text from the URL.
     * @return document
     * @throws RuntimeException If specified URL is malformed.
     */
    public static Document load(String url, DocumentParser documentParser) {
        return load(createUrl(url), documentParser);
    }

    /**
     * Creates a URL from the specified string.
     * @param url The URL string.
     * @return the URL
     * @throws IllegalArgumentException If specified URL is malformed.
     */
    static URL createUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
