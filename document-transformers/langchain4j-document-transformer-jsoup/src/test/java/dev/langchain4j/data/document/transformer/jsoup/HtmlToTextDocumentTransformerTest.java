package dev.langchain4j.data.document.transformer.jsoup;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlToTextDocumentTransformerTest {

    private static final String SAMPLE_HTML = "<html>" +
            "<body>" +
            "<h1 id=\"title\">Title</h1>" +
            "<p id=\"p1\">Paragraph 1<br>Something</p>" +
            "<p id=\"p2\">Paragraph 2</p>" +
            "<p id=\"p3\">More details <a href=\"http://example.org\">here</a>.</p>" +
            "List:<ul><li>Item one</li><li>Item two</li></ul>" +
            "</body>" +
            "</html>";

    private static final String SAMPLE_HTML_WITH_RELATIVE_LINKS = "<html>" +
            "<body>" +
            "<p>Follow the link <a href=\"/menu1\">here</a>.</p>" +
            "</body>" +
            "</html>";

    @Test
    void should_extract_all_text_from_html() {

        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer();
        Document htmlDocument = Document.from(SAMPLE_HTML);

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo(
                "Title\n" +
                        "\n" +
                        "Paragraph 1\n" +
                        "Something\n" +
                        "\n" +
                        "Paragraph 2\n" +
                        "\n" +
                        "More details here.\n" +
                        "List:\n" +
                        " * Item one\n" +
                        " * Item two"
        );
    }

    @Test
    void should_extract_text_from_html_by_css_selector() {

        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer("#p1", null, false);
        Document htmlDocument = Document.from(SAMPLE_HTML);

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo("Paragraph 1\nSomething");
        assertThat(transformedDocument.metadata().toMap()).isEmpty();
    }

    @Test
    void should_extract_text_and_metadata_from_html_by_css_selectors() {

        Map<String, String> metadataCssSelectors = new HashMap<>();
        metadataCssSelectors.put("title", "#title");

        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer("#p1", metadataCssSelectors, false);
        Document htmlDocument = Document.from(SAMPLE_HTML);

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo("Paragraph 1\nSomething");

        assertThat(transformedDocument.metadata().toMap()).hasSize(1);
        assertThat(transformedDocument.metadata().getString("title")).isEqualTo("Title");
    }

    @Test
    void should_extract_text_with_links_from_html() {

        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer(null, null, true);
        Document htmlDocument = Document.from(SAMPLE_HTML);

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo(
                "Title\n" +
                        "\n" +
                        "Paragraph 1\n" +
                        "Something\n" +
                        "\n" +
                        "Paragraph 2\n" +
                        "\n" +
                        "More details here <http://example.org>.\n" +
                        "List:\n" +
                        " * Item one\n" +
                        " * Item two"
        );
        assertThat(transformedDocument.metadata().toMap()).isEmpty();
    }

    @Test
    void should_extract_text_with_absolute_links_from_html_with_relative_links_and_url_metadata() {
        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer(null, null, true);
        Document htmlDocument = Document.from(SAMPLE_HTML_WITH_RELATIVE_LINKS);
        htmlDocument.metadata().put(Document.URL, "https://example.org/page.html");

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo(
                "Follow the link here <https://example.org/menu1>."
        );
        assertThat(transformedDocument.metadata().toMap())
                .containsEntry(Document.URL, "https://example.org/page.html")
                .hasSize(1);
    }

    @Test
    void should_extract_text_with_absolute_links_from_html_with_absolute_links_and_url_metadata() {
        HtmlToTextDocumentTransformer transformer = new HtmlToTextDocumentTransformer(null, null, true);
        Document htmlDocument = Document.from(SAMPLE_HTML);
        htmlDocument.metadata().put(Document.URL, "https://other.example.org/page.html");

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo(
                "Title\n" +
                        "\n" +
                        "Paragraph 1\n" +
                        "Something\n" +
                        "\n" +
                        "Paragraph 2\n" +
                        "\n" +
                        "More details here <http://example.org>.\n" +
                        "List:\n" +
                        " * Item one\n" +
                        " * Item two"
        );
        assertThat(transformedDocument.metadata().toMap())
                .containsEntry(Document.URL, "https://other.example.org/page.html")
                .hasSize(1);
    }
}
