package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentType;
import dev.langchain4j.data.document.Metadata;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static dev.langchain4j.data.document.Document.DOCUMENT_TYPE;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TextDocumentParser implements DocumentParser {

    private final DocumentType documentType;
    private final Charset charset;

    public TextDocumentParser(DocumentType documentType) {
        this(documentType, UTF_8);
    }

    public TextDocumentParser(DocumentType documentType, Charset charset) {
        this.documentType = ensureNotNull(documentType, "documentType");
        this.charset = ensureNotNull(charset, "charset");
    }

    @Override
    public Document parse(InputStream inputStream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            String text = new String(buffer.toByteArray(), charset);

            return Document.from(text, Metadata.from(DOCUMENT_TYPE, documentType.toString()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
