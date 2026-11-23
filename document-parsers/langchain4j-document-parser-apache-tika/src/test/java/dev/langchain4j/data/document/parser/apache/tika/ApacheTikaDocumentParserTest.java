package dev.langchain4j.data.document.parser.apache.tika;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApacheTikaDocumentParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"test-file.doc", "test-file.docx", "test-file.ppt", "test-file.pptx", "test-file.pdf"})
    void should_parse_doc_ppt_and_pdf_files(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-file.doc", "test-file.docx", "test-file.ppt", "test-file.pptx", "test-file.pdf"})
    void should_parse_doc_ppt_and_pdf_files_with_metadata(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser(AutoDetectParser::new, null, null, null, true);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");

        final Map<String, Object> metadata = document.metadata().toMap();
        assertThat(metadata).isNotEmpty();
        assertThat(metadata.get("X-TIKA:Parsed-By")).isNotNull();
        assertThat(metadata.get("X-TIKA:Parsed-By-Full-Set")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-file.xls", "test-file.xlsx"})
    void should_parse_xls_files(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser(AutoDetectParser::new, null, null, null, false);
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @Test
    void should_parse_files_stateless() {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream1 = getClass().getClassLoader().getResourceAsStream("test-file.xls");
        InputStream inputStream2 = getClass().getClassLoader().getResourceAsStream("test-file.xls");

        Document document1 = parser.parse(inputStream1);
        Document document2 = parser.parse(inputStream2);

        assertThat(document1.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document2.text()).isEqualToIgnoringWhitespace("Sheet1\ntest content\nSheet2\ntest content");
        assertThat(document1.metadata().toMap()).isEmpty();
        assertThat(document2.metadata().toMap()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"empty-file.txt", "blank-file.txt", "blank-file.docx", "blank-file.pptx"
                // "blank-file.xlsx" TODO
            })
    void should_throw_BlankDocumentException(String fileName) {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        assertThatThrownBy(() -> parser.parse(inputStream)).isExactlyInstanceOf(BlankDocumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-file.doc", "test-file.docx", "test-file.ppt", "test-file.pptx", "test-file.pdf"})
    void should_parse_files_with_builder(String fileName) {

        DocumentParser parser = ApacheTikaDocumentParser.builder().build();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().toMap()).isEmpty();
    }

    @Test
    void should_parse_file_with_metadata_with_builder() {

        DocumentParser parser = ApacheTikaDocumentParser.builder()
                .parserSupplier(AutoDetectParser::new)
                .includeMetadata(true)
                .build();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-file.docx");

        Document document = parser.parse(inputStream);

        assertThat(document.text()).isEqualToIgnoringWhitespace("test content");
        assertThat(document.metadata().toMap()).containsKey("X-TIKA:Parsed-By");
    }

    @Test
    void should_parse_text_within_max_characters() {

        DocumentParser parser =
                ApacheTikaDocumentParser.builder().maxCharacters(1_000).build();

        Document document = parser.parse(textStream("a".repeat(900)));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(900));
    }

    @Test
    void should_fail_when_text_has_more_than_max_characters() {

        DocumentParser parser =
                ApacheTikaDocumentParser.builder().maxCharacters(1_000).build();

        assertThatThrownBy(() -> parser.parse(textStream("a".repeat(1_001))))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("The document contains more than 1000 characters of text, "
                        + "which is the maximum this parser extracts. To parse it, set a higher limit with "
                        + "ApacheTikaDocumentParser.builder().maxCharacters(...), or -1 for no limit.")
                .cause()
                .isInstanceOf(WriteLimitReachedException.class);
    }

    @Test
    void should_fail_when_docx_has_more_than_max_characters() throws IOException {

        DocumentParser parser =
                ApacheTikaDocumentParser.builder().maxCharacters(1_000).build();
        InputStream docx = docxWithParagraphs(100, "a".repeat(100));

        assertThatThrownBy(() -> parser.parse(docx))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("The document contains more than 1000 characters of text")
                .cause()
                .isInstanceOf(WriteLimitReachedException.class);
    }

    @Test
    void should_parse_docx_within_max_characters() throws IOException {

        DocumentParser parser =
                ApacheTikaDocumentParser.builder().maxCharacters(20_000).build();

        Document document = parser.parse(docxWithParagraphs(100, "a".repeat(100)));

        assertThat(document.text()).isEqualToIgnoringWhitespace("a".repeat(10_000));
    }

    @Test
    void should_fail_by_default_when_text_has_more_than_default_max_characters() {

        DocumentParser parser = new ApacheTikaDocumentParser();
        InputStream text = textStream("a".repeat(ApacheTikaDocumentParser.DEFAULT_MAX_CHARACTERS + 1));

        assertThatThrownBy(() -> parser.parse(text))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("The document contains more than 10000000 characters of text")
                .cause()
                .isInstanceOf(WriteLimitReachedException.class);
    }

    @Test
    void should_parse_text_with_more_than_default_max_characters_when_limit_is_disabled() {

        DocumentParser parser =
                ApacheTikaDocumentParser.builder().maxCharacters(-1).build();
        int length = ApacheTikaDocumentParser.DEFAULT_MAX_CHARACTERS + 1;

        Document document = parser.parse(textStream("a".repeat(length)));

        assertThat(document.text().strip()).hasSize(length);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2, Integer.MIN_VALUE})
    void should_reject_invalid_max_characters(int maxCharacters) {

        assertThatThrownBy(() -> ApacheTikaDocumentParser.builder()
                        .maxCharacters(maxCharacters)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxCharacters must be a positive number, or -1 for no limit, but was: " + maxCharacters);
    }

    @Test
    void should_reject_max_characters_together_with_content_handler_supplier() {

        assertThatThrownBy(() -> ApacheTikaDocumentParser.builder()
                        .contentHandlerSupplier(() -> new BodyContentHandler(1_000))
                        .maxCharacters(1_000)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxCharacters applies only to the default content handler");
    }

    @Test
    void should_use_limit_of_custom_content_handler() {

        DocumentParser parser = ApacheTikaDocumentParser.builder()
                .contentHandlerSupplier(() -> new BodyContentHandler(10))
                .build();

        assertThatThrownBy(() -> parser.parse(textStream("a".repeat(11))))
                .isExactlyInstanceOf(RuntimeException.class)
                .cause()
                .isInstanceOf(WriteLimitReachedException.class);
    }

    @Test
    void should_use_default_content_handler_when_content_handler_is_null() {

        DocumentParser parser = new ApacheTikaDocumentParser(null, null, null, null, false);

        assertThatThrownBy(() -> parser.parse(
                        textStream("a".repeat(ApacheTikaDocumentParser.DEFAULT_MAX_CHARACTERS + 1))))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageStartingWith("The document contains more than 10000000 characters of text");
    }

    private static InputStream textStream(String text) {
        return new ByteArrayInputStream(text.getBytes(UTF_8));
    }

    private static InputStream docxWithParagraphs(int paragraphs, String paragraphText) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            putEntry(
                    zip,
                    "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                            + "<Default Extension=\"rels\" "
                            + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/"
                            + "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                            + "</Types>");
            putEntry(
                    zip,
                    "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/"
                            + "2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                            + "</Relationships>");
            String paragraph = "<w:p><w:r><w:t>" + paragraphText + "</w:t></w:r></w:p>";
            putEntry(
                    zip,
                    "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                            + "<w:body>" + paragraph.repeat(paragraphs) + "</w:body></w:document>");
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(UTF_8));
        zip.closeEntry();
    }
}
