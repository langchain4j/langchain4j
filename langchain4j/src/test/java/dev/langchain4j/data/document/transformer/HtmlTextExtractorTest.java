package dev.langchain4j.data.document.transformer;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.document.DocumentParser.DOCUMENT_TYPE;
import static dev.langchain4j.data.document.DocumentType.HTML;
import static dev.langchain4j.data.document.Metadata.metadata;
import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextExtractorTest {

    private static final String SAMPLE_HTML = "<html>" +
            "<body>" +
            "<h1>Title</h1>" +
            "<p id=\"p1\">Paragraph 1<br>Something</p>" +
            "<p id=\"p2\">Paragraph 2</p>" +
            "List:<ul><li>Item one</li><li>Item two</li></ul>" +
            "</body>" +
            "</html>";

    @Test
    void should_extract_all_text_from_html() {

        HtmlTextExtractor transformer = new HtmlTextExtractor();
        Document htmlDocument = Document.from(SAMPLE_HTML, metadata(DOCUMENT_TYPE, HTML.toString()));

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo(
                "Title\n" +
                        "\n" +
                        "Paragraph 1\n" +
                        "Something\n" +
                        "\n" +
                        "Paragraph 2\n" +
                        "List:\n" +
                        " * Item one\n" +
                        " * Item two"
        );
    }

    @Test
    void should_extract_text_from_html_by_css_selector() {

        HtmlTextExtractor transformer = new HtmlTextExtractor("#p1", false);
        Document htmlDocument = Document.from(SAMPLE_HTML, metadata(DOCUMENT_TYPE, HTML.toString()));

        Document transformedDocument = transformer.transform(htmlDocument);

        assertThat(transformedDocument.text()).isEqualTo("Paragraph 1\nSomething");
    }
}