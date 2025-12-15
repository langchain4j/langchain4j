package dev.langchain4j.data.document;

import dev.langchain4j.exception.LangChain4jException;

public class BlankDocumentException extends LangChain4jException {

    public BlankDocumentException() {
        super("The document is blank");
    }
}
