package dev.langchain4j.data.document;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentSource {

    InputStream inputStream() throws IOException;

    Metadata sourceMetadata();
}
