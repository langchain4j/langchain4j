package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.jsoup.Jsoup;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HtmlDocumentParser implements DocumentParser {

    private final Charset charset;

    public HtmlDocumentParser() {
        this(UTF_8);
    }

    public HtmlDocumentParser(Charset charset) {
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

            String html = new String(buffer.toByteArray(), charset);

            org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);

            String text = jsoupDocument.select("#main-content").text();

            Metadata metadata = new Metadata();
            metadata.add("title", jsoupDocument.select("#title-text").text());

            return Document.from(text, metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
