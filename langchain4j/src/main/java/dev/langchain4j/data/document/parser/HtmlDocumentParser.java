package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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

            List<String> textNodes = new ArrayList<>();

            NodeVisitor nodeVisitor = new NodeVisitor() {
                public void head(Node node, int depth) {
                    if (node instanceof TextNode) {
                        String text = ((TextNode) node).text();
                        if (!text.trim().isEmpty()) {
                            textNodes.add(text);
                        }
                    }
                }

                public void tail(Node node, int depth) {
                    // do nothing
                }
            };
            org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
            NodeTraversor.traverse(nodeVisitor, jsoupDocument.select("#main-content")); // TODO config

            Metadata metadata = new Metadata();
            metadata.add("title", jsoupDocument.select("#title-text").text());

            String text = String.join("\n", textNodes); // TODO nodes?

            return Document.from(text, metadata);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
