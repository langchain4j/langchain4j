package dev.langchain4j.data.document;

import static java.lang.String.format;

public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String filePath) {
        super(format("Document type of '%s' is not supported", filePath));
    }
}
