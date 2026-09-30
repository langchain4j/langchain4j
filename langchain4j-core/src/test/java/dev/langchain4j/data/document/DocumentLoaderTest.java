package dev.langchain4j.data.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class DocumentLoaderTest implements WithAssertions {

    private static final DocumentParser COLLIDING_PARSER = inputStream ->
            Document.from("Hello, world!", new Metadata().put("foo", "baz").put("title", "Bar"));

    public static final class StringSource implements DocumentSource {
        private final String content;
        private final Metadata metadata;

        public StringSource(String content, Metadata metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        @Override
        public InputStream inputStream() {
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Metadata metadata() {
            return metadata;
        }
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 4 * 0x400; // 4KB
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                while ((readLen = inputStream.read(buf, 0, bufLen)) != -1) outputStream.write(buf, 0, readLen);

                return outputStream.toByteArray();
            }
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null) inputStream.close();
            else
                try {
                    inputStream.close();
                } catch (IOException e) {
                    exception.addSuppressed(e);
                }
        }
    }

    public static final class TrivialParser implements DocumentParser {
        @Override
        public Document parse(InputStream inputStream) {
            String str;
            try {
                str = new String(readAllBytes(inputStream), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read input stream", e);
            }

            return Document.from(str);
        }
    }

    @Test
    void load() {
        StringSource source = new StringSource("Hello, world!", new Metadata().put("foo", "bar"));
        Document document = DocumentLoader.load(source, new TrivialParser());
        assertThat(document).isEqualTo(Document.from("Hello, world!", new Metadata().put("foo", "bar")));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> DocumentLoader.load(
                        new DocumentSource() {
                            @Override
                            public InputStream inputStream() throws IOException {
                                throw new IOException("Failed to open input stream");
                            }

                            @Override
                            public Metadata metadata() {
                                return new Metadata();
                            }
                        },
                        new TrivialParser()))
                .withMessageContaining("Failed to load document");

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> DocumentLoader.load(source, inputStream -> {
                    throw new RuntimeException("Failed to parse document");
                }))
                .withMessageContaining("Failed to load document");
    }

    @Test
    void load_sourceWinsWhenDocumentAndSourceMetadataShareKeys() {
        StringSource source = new StringSource("Hello, world!", new Metadata().put("foo", "bar"));

        Document document = DocumentLoader.load(source, COLLIDING_PARSER);

        assertThat(document.metadata().toMap()).containsOnly(entry("foo", "bar"), entry("title", "Bar"));
    }

    @Test
    void load_wrapsIllegalArgumentExceptionThrownByParser() {
        StringSource source = new StringSource("Hello, world!", new Metadata().put("foo", "bar"));
        DocumentParser failingParser = inputStream -> {
            throw new IllegalArgumentException("Failed to parse document");
        };

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> DocumentLoader.load(source, failingParser))
                .withMessageContaining("Failed to load document")
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describeConflicts_reportsSharedKeysWithDifferentValues() {
        Metadata documentMetadata = new Metadata().put("file_name", "report.pdf");
        Map<String, Object> sourceMetadata =
                new Metadata().put("file_name", "2024-report.pdf").toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .containsExactly("file_name=(document=\"report.pdf\", source=\"2024-report.pdf\")");
    }

    @Test
    void describeConflicts_ignoresSharedKeysWithEqualValues() {
        Metadata documentMetadata = new Metadata().put("file_name", "report.pdf");
        Map<String, Object> sourceMetadata =
                new Metadata().put("file_name", "report.pdf").toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .isEmpty();
    }

    @Test
    void describeConflicts_ignoresKeysSetByOnlyOneSide() {
        Metadata documentMetadata = new Metadata().put("title", "Bar");
        Map<String, Object> sourceMetadata =
                new Metadata().put("absolute_directory_path", "/docs").toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .isEmpty();
    }

    @Test
    void describeConflicts_reportsAllConflictsSortedByKey() {
        Metadata documentMetadata = new Metadata()
                .put("title", "Bar")
                .put("author", "Alice")
                .put("file_name", "report.pdf")
                .put("language", "en");
        Map<String, Object> sourceMetadata = new Metadata()
                .put("title", "Baz")
                .put("author", "Alice")
                .put("file_name", "2024-report.pdf")
                .put("absolute_directory_path", "/docs")
                .toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .containsExactly(
                        "file_name=(document=\"report.pdf\", source=\"2024-report.pdf\")",
                        "title=(document=\"Bar\", source=\"Baz\")");
    }

    @Test
    void describeConflicts_addsTypesWhenValuesOfDifferentTypesRenderTheSame() {
        Metadata documentMetadata = new Metadata().put("page", 5);
        Map<String, Object> sourceMetadata = new Metadata().put("page", "5").toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .containsExactly("page=(document=\"5\" (Integer), source=\"5\" (String))");
    }

    @Test
    void describeConflicts_omitsTypesWhenValuesRenderDifferently() {
        Metadata documentMetadata = new Metadata().put("page", 5);
        Map<String, Object> sourceMetadata = new Metadata().put("page", "6").toMap();

        assertThat(DocumentLoader.describeConflicts(documentMetadata, sourceMetadata))
                .containsExactly("page=(document=\"5\", source=\"6\")");
    }

    @Test
    void load_keepsSourceValueWhenSharedKeysHaveEqualValues() {
        StringSource source = new StringSource("Hello, world!", new Metadata().put("foo", "baz"));

        Document document = DocumentLoader.load(source, COLLIDING_PARSER);

        assertThat(document.metadata().toMap()).containsOnly(entry("foo", "baz"), entry("title", "Bar"));
    }
}
