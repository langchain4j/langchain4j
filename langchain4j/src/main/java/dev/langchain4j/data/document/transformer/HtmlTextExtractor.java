package dev.langchain4j.data.document.transformer;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.data.document.Document.URL;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.jsoup.internal.StringUtil.in;
import static org.jsoup.select.NodeTraversor.traverse;

/**
 * Extracts text from a given HTML document.
 * A CSS selector can be specified to extract text only from desired element(s).
 * Also, multiple CSS selectors can be specified to extract metadata from desired elements.
 */
public class HtmlTextExtractor implements DocumentTransformer {

    private static final Logger log = LoggerFactory.getLogger(HtmlTextExtractor.class);

    private final String cssSelector;
    private final Map<String, String> metadataCssSelectors;
    private final boolean includeLinks;

    /**
     * Constructs an instance of HtmlToTextTransformer that extracts all text from a given Document containing HTML.
     */
    public HtmlTextExtractor() {
        this(null, null, false);
    }

    /**
     * Constructs an instance of HtmlToTextTransformer that extracts text from HTML elements matching the provided CSS selector.
     *
     * @param cssSelector          A CSS selector.
     *                             For example, "#page-content" will extract text from the HTML element with the id "page-content".
     * @param metadataCssSelectors A mapping from metadata keys to CSS selectors.
     *                             For example, Mep.of("title", "#page-title") will extract all text from the HTML element
     *                             with id "title" and store it in {@link Metadata} under the key "title".
     * @param includeLinks         Specifies whether links should be included in the extracted text.
     */
    public HtmlTextExtractor(String cssSelector, Map<String, String> metadataCssSelectors, boolean includeLinks) {
        this.cssSelector = cssSelector;
        this.metadataCssSelectors = metadataCssSelectors;
        this.includeLinks = includeLinks;
    }

    @Override
    public Document transform(Document document) {
        String html = document.text();
        String baseUrl = document.metadata(URL) != null ? document.metadata(URL) : "";
        org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html, baseUrl);

        String text;
        if (cssSelector != null) {
            text = extractText(jsoupDocument, cssSelector, includeLinks);
        } else {
            text = extractText(jsoupDocument, includeLinks);
        }

        Metadata metadata = document.metadata().copy();
        if (metadataCssSelectors != null) {
            metadataCssSelectors.forEach((metadataKey, cssSelector) ->
                    metadata.put(metadataKey, jsoupDocument.select(cssSelector).text()));
        }

        return Document.from(text, metadata);
    }

    private static String extractText(org.jsoup.nodes.Document jsoupDocument, String cssSelector, boolean includeLinks) {
        return jsoupDocument.select(cssSelector).stream()
                .map(element -> extractText(element, includeLinks))
                .collect(joining("\n\n"));
    }

    private static String extractText(Element element, boolean includeLinks) {
        NodeVisitor visitor = new TextExtractingVisitor(includeLinks);
        traverse(visitor, element);
        return visitor.toString().trim();
    }

    // taken from https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/examples/HtmlToPlainText.java
    private static class TextExtractingVisitor implements NodeVisitor {

        private final StringBuilder textBuilder = new StringBuilder();
        private final boolean includeLinks;

        private TextExtractingVisitor(boolean includeLinks) {
            this.includeLinks = includeLinks;
        }

        @Override
        public void head(Node node, int depth) { // hit when the node is first seen
            String name = node.nodeName();
            if (node instanceof TextNode)
                textBuilder.append(((TextNode) node).text());
            else if (name.equals("li"))
                textBuilder.append("\n * ");
            else if (name.equals("dt"))
                textBuilder.append("  ");
            else if (in(name, "p", "h1", "h2", "h3", "h4", "h5", "h6", "tr"))
                textBuilder.append("\n");
        }

        @Override
        public void tail(Node node, int depth) { // hit when all the node's children (if any) have been visited
            String name = node.nodeName();
            if (in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5", "h6"))
                textBuilder.append("\n");
            else if (includeLinks && name.equals("a")) {
                String link = node.absUrl("href");
                if (link.isEmpty() && node.baseUri().isEmpty()) {
                    log.warn("No 'URL' metadata found for document. Link will be empty");
                }
                textBuilder.append(format(" <%s>", link));
            }
        }

        @Override
        public String toString() {
            return textBuilder.toString();
        }
    }
}