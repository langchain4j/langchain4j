package dev.langchain4j.data.document.parser.apache.pdfbox;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApachePdfBoxDocumentParserTest {

    @Test
    void should_parse_pdf_file() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap()).isEmpty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_throw_BlankDocumentException() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("blank-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser();
            assertThatThrownBy(() -> parser.parse(inputStream))
                    .isExactlyInstanceOf(BlankDocumentException.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_parse_pdf_file_include_metadata() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser = new ApachePdfBoxDocumentParser(true);
            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap())
                    .containsEntry("Author", "ljuba")
                    .containsEntry("Creator", "WPS Writer")
                    .containsEntry("CreationDate", "D:20230608171011+15'10'")
                    .containsEntry("SourceModified", "D:20230608171011+15'10'");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void should_parse_pdf_file_with_builder() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser = ApachePdfBoxDocumentParser.builder().build();

            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap()).isEmpty();
        }
    }

    @Test
    void should_parse_pdf_file_include_metadata_with_builder() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.pdf")) {
            DocumentParser parser =
                    ApachePdfBoxDocumentParser.builder().includeMetadata(true).build();

            Document document = parser.parse(inputStream);

            assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
            assertThat(document.metadata().toMap()).containsEntry("Author", "ljuba");
        }
    }

    @Test
    void should_parse_page_with_max_characters_per_page() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(100).build();

        Document document = parser.parse(pdfWithPages(100));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(100));
    }

    @Test
    void should_fail_when_page_has_more_than_max_characters_per_page() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(100).build();
        InputStream pdf = pdfWithPages(10, 101);

        assertThatThrownBy(() -> parser.parse(pdf))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Page 2 of the PDF document contains more than 100 characters, "
                        + "which is the maximum this parser extracts from a page. To parse it, set a higher limit with "
                        + "ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(...), or -1 for no limit.");
    }

    @Test
    void should_count_max_characters_per_page_for_every_page() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(100).build();

        Document document = parser.parse(pdfWithPages(80, 80, 80));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(240));
    }

    @Test
    void should_parse_document_with_max_characters() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharacters(100).build();

        Document document = parser.parse(pdfWithPages(50, 50));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(100));
    }

    @Test
    void should_fail_when_document_has_more_than_max_characters() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharacters(100).build();
        InputStream pdf = pdfWithPages(60, 60);

        assertThatThrownBy(() -> parser.parse(pdf))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("The PDF document contains more than 100 characters, "
                        + "which is the maximum this parser extracts. To parse it, set a higher limit with "
                        + "ApachePdfBoxDocumentParser.builder().maxCharacters(...), or -1 for no limit.");
    }

    @Test
    void should_fail_by_default_when_page_has_more_than_100_000_characters() throws IOException {
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        InputStream pdf = pdfWithPages(100_001);

        assertThatThrownBy(() -> parser.parse(pdf))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("Page 1 of the PDF document contains more than 100000 characters");
    }

    @Test
    void should_parse_page_with_more_than_100_000_characters_when_limit_per_page_is_disabled() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharactersPerPage(-1).build();

        Document document = parser.parse(pdfWithPages(100_001));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(100_001));
    }

    @Test
    void should_fail_by_default_when_document_has_more_than_10_000_000_characters() throws IOException {
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        InputStream pdf = pdfWithSamePage(101, 100_000);

        assertThatThrownBy(() -> parser.parse(pdf))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("The PDF document contains more than 10000000 characters");
    }

    @Test
    void should_parse_document_with_more_than_10_000_000_characters_when_limit_is_disabled() throws IOException {
        DocumentParser parser =
                ApachePdfBoxDocumentParser.builder().maxCharacters(-1).build();

        Document document = parser.parse(pdfWithSamePage(101, 100_000));

        assertThat(document.text().chars().filter(c -> c == 'a').count()).isEqualTo(10_100_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2, Integer.MIN_VALUE})
    void should_reject_invalid_max_characters(int maxCharacters) {
        assertThatThrownBy(() -> ApachePdfBoxDocumentParser.builder()
                        .maxCharacters(maxCharacters)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCharacters must be a positive number, or -1 for no limit, but was: " + maxCharacters);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2, Integer.MIN_VALUE})
    void should_reject_invalid_max_characters_per_page(int maxCharactersPerPage) {
        assertThatThrownBy(() -> ApachePdfBoxDocumentParser.builder()
                        .maxCharactersPerPage(maxCharactersPerPage)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCharactersPerPage must be a positive number, or -1 for no limit, but was: "
                        + maxCharactersPerPage);
    }

    private static InputStream pdfWithPages(int... charactersPerPage) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int characters : charactersPerPage) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 1);
                    content.newLineAtOffset(10, 700);
                    content.showText("a".repeat(characters));
                    content.endText();
                }
            }
            return toInputStream(document);
        }
    }

    private static InputStream pdfWithSamePage(int pages, int charactersPerPage) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("F1"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            PDStream content = new PDStream(document);
            try (OutputStream out = content.createOutputStream(COSName.FLATE_DECODE)) {
                out.write(("BT /F1 1 Tf 10 700 Td (" + "a".repeat(charactersPerPage) + ") Tj ET").getBytes(US_ASCII));
            }
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                page.setResources(resources);
                page.setContents(content);
                document.addPage(page);
            }
            return toInputStream(document);
        }
    }

    private static InputStream toInputStream(PDDocument document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        document.save(bytes);
        return new ByteArrayInputStream(bytes.toByteArray());
    }
}
