package dev.langchain4j.data.document.parser.docling;

import static org.assertj.core.api.Assertions.assertThat;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.testcontainers.serve.DoclingServeContainer;
import ai.docling.testcontainers.serve.config.DoclingServeContainerConfig;
import dev.langchain4j.data.document.Document;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DoclingDocumentParserIT {

    @Container
    private static final DoclingServeContainer doclingContainer =
            new DoclingServeContainer(DoclingServeContainerConfig.builder()
                    .image(DoclingServeContainerConfig.DOCLING_IMAGE)
                    .enableUi(false)
                    .build());

    private final DoclingServeApi client = DoclingServeApi.builder()
            .baseUrl(doclingContainer.getApiUrl())
            .logRequests()
            .logResponses()
            .prettyPrint()
            .build();

    @Test
    void shouldParsePdfDocument() throws Exception {
        DoclingDocumentParser parser = new DoclingDocumentParser(client);

        try (InputStream inputStream = getClass().getResourceAsStream("/test-file.pdf")) {
            Document document = parser.parse(inputStream);

            assertThat(document).isNotNull();
            assertThat(document.text()).isNotEmpty();
            assertThat(document.metadata().getString("document_size_bytes")).isNotNull();
        }
    }

    @Test
    void shouldParseDocxDocument() throws Exception {
        DoclingDocumentParser parser = new DoclingDocumentParser(client);

        try (InputStream inputStream = getClass().getResourceAsStream("/test-file.docx")) {
            Document document = parser.parse(inputStream);

            assertThat(document).isNotNull();
            assertThat(document.text()).isNotEmpty();
        }
    }
}
