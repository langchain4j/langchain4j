package dev.langchain4j.data.document;

import dev.langchain4j.data.document.source.UrlSource;

import java.net.MalformedURLException;
import java.net.URL;

import static dev.langchain4j.data.document.DocumentLoaderUtils.detectDocumentType;
import static dev.langchain4j.data.document.DocumentLoaderUtils.parserFor;

public class UrlDocumentLoader {

    /**
     * Loads a document from the specified URL, detecting the document type automatically.
     *
     * @param url URL of the file
     * @return document
     * @throws UnsupportedDocumentTypeException if document type is not supported or cannot be detected automatically
     */
    public static Document load(URL url) {
        return load(url, detectDocumentType(url.toString()));
    }

    /**
     * Loads a document from the specified URL, detecting the document type automatically.
     *
     * @param url URL of the file
     * @return document
     * @throws RuntimeException                 if specified URL is malformed
     * @throws UnsupportedDocumentTypeException if document type is not supported or cannot be detected automatically
     */
    public static Document load(String url) {
        try {
            return load(new URL(url));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a document from the specified URL.
     *
     * @param url          URL of the file
     * @param documentType type of the document
     * @return document
     */
    public static Document load(URL url, DocumentType documentType) {
        return DocumentLoaderUtils.load(UrlSource.from(url), parserFor(documentType));
    }

    /**
     * Loads a document from the specified URL.
     *
     * @param url          URL of the file
     * @param documentType type of the document
     * @return document
     * @throws RuntimeException if specified URL is malformed
     */
    public static Document load(String url, DocumentType documentType) {
        try {
            return load(new URL(url), documentType);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
