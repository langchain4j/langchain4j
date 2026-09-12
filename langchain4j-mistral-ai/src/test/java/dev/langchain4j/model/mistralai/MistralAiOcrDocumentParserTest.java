package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MistralAiOcrDocumentParserTest {

    private static final String RESPONSE = """
            {
              "pages": [{ "index": 0, "markdown": "# Scanned" }],
              "model": "mistral-ocr-2505",
              "usage_info": { "pages_processed": 1 }
            }
            """;

    @Test
    void should_parse_stream_and_report_input_size() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        DocumentParser parser = MistralAiOcrDocumentParser.builder()
                .ocrModel(MistralAiOcrModel.builder()
                        .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                        .apiKey("dummy")
                        .modelName("mistral-ocr-latest")
                        .build())
                .build();

        // when
        Document document = parser.parse(new ByteArrayInputStream("hello".getBytes()));

        // then
        assertThat(document.text()).isEqualTo("# Scanned");
        assertThat(document.metadata().getInteger(MistralAiOcrDocumentParser.DOCUMENT_SIZE_BYTES))
                .isEqualTo(5);
        assertThat(mockHttpClient.request().body()).contains("data:application/pdf;base64,aGVsbG8=");
    }

    @Test
    void should_use_configured_mime_type() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        DocumentParser parser = MistralAiOcrDocumentParser.builder()
                .ocrModel(MistralAiOcrModel.builder()
                        .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                        .apiKey("dummy")
                        .modelName("mistral-ocr-latest")
                        .build())
                .mimeType("image/png")
                .build();

        // when
        parser.parse(new ByteArrayInputStream("hello".getBytes()));

        // then
        assertThat(mockHttpClient.request().body()).contains("data:image/png;base64,aGVsbG8=");
    }

    @Test
    void should_throw_for_empty_stream_without_calling_the_service() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        DocumentParser parser = MistralAiOcrDocumentParser.builder()
                .ocrModel(MistralAiOcrModel.builder()
                        .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                        .apiKey("dummy")
                        .modelName("mistral-ocr-latest")
                        .build())
                .build();

        // when, then
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BlankDocumentException.class);
        assertThat(mockHttpClient.requests()).isEmpty();
    }
}
