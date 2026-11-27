package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiOcrModelIT {

    /**
     * A publicly reachable multi-page PDF, so that the round trip can be exercised without a fixture.
     */
    private static final String DOCUMENT_URL = "https://arxiv.org/pdf/2201.04234";

    MistralAiOcrModel model = MistralAiOcrModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName("mistral-ocr-latest")
            .logRequests(true)
            .build();

    @Test
    void should_extract_content_of_a_document_url() {
        Document document = model.parseDocumentUrl(DOCUMENT_URL);

        assertThat(document.text()).isNotBlank();
        assertThat(document.metadata().getString(MistralAiOcrModel.OCR_MODEL)).isNotBlank();
        assertThat(document.metadata().getInteger(MistralAiOcrModel.PAGES_PROCESSED))
                .isPositive();
    }

    /**
     * Pages without recognized content are skipped, so the page indices may have gaps, but they have to
     * stay in document order.
     */
    @Test
    void should_return_one_document_per_page() {
        List<Document> documents = model.parsePages(readDocument(), "application/pdf");

        assertThat(documents).isNotEmpty();
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.text()).isNotBlank();
            assertThat(document.metadata().getInteger(MistralAiOcrModel.PAGE_INDEX))
                    .isNotNegative();
        });

        List<Integer> indices = documents.stream()
                .map(document -> document.metadata().getInteger(MistralAiOcrModel.PAGE_INDEX))
                .toList();
        assertThat(indices).isSorted();
    }

    private static byte[] readDocument() {
        try (InputStream inputStream = URI.create(DOCUMENT_URL).toURL().openStream()) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
