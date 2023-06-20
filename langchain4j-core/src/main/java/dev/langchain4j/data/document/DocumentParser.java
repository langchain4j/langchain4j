package dev.langchain4j.data.document;

import java.io.InputStream;

public interface DocumentParser {

    Document parse(InputStream inputStream);
}
