package dev.langchain4j.data.document.transformer;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentTransformer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.jsoup.internal.StringUtil.in;
import static org.jsoup.select.NodeTraversor.traverse;

/**
 * Extracts text from a given HTML document.
 * A CSS selector can be specified to extract text only from desired element(s).
 */
public class HtmlTextExtractor implements DocumentTransformer {

    private final String cssSelector;

    /**
     * Constructs an instance of HtmlToTextTransformer that extracts all text from a given Document containing HTML.
     */
    public HtmlTextExtractor() {
        this(null);
    }

    /**
     * Constructs an instance of HtmlToTextTransformer that extracts text from HTML elements matching the provided CSS selector.
     *
     * @param cssSelector A CSS selector. For example, '#content' will extract all text from the HTML element with id "content".
     */
    public HtmlTextExtractor(String cssSelector) {
        this.cssSelector = cssSelector;
    }

    @Override
    public Document transform(Document document) {
        String html = document.text();
        org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);

        String text;
        if (cssSelector != null) {
            text = extractText(jsoupDocument, cssSelector);
        } else {
            text = extractText(jsoupDocument);
        }

        return Document.from(text, document.metadata());
    }

    private static String extractText(org.jsoup.nodes.Document jsoupDocument, String cssSelector) {
        return jsoupDocument.select(cssSelector).stream()
                .map(HtmlTextExtractor::extractText)
                .collect(joining("\n\n"));
    }

    private static String extractText(Element element) {
        NodeVisitor visitor = new TextExtractingVisitor();
        traverse(visitor, element);
        return visitor.toString().trim();
    }

    // taken from https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/examples/HtmlToPlainText.java
    private static class TextExtractingVisitor implements NodeVisitor {

        private final StringBuilder textBuilder = new StringBuilder();

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
            else if (name.equals("a"))
                textBuilder.append(format(" <%s>", node.absUrl("href")));
        }

        @Override
        public String toString() {
            return textBuilder.toString();
        }
    }
}
