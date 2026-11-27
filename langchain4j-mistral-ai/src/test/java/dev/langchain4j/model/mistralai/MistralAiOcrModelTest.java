package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MistralAiOcrModelTest {

    private static final String RESPONSE = """
            {
              "pages": [
                {
                  "index": 0,
                  "markdown": "# Invoice",
                  "images": [
                    {
                      "id": "img-0.jpeg",
                      "top_left_x": 100,
                      "top_left_y": 120,
                      "bottom_right_x": 300,
                      "bottom_right_y": 320
                    }
                  ],
                  "dimensions": { "dpi": 200, "height": 2200, "width": 1700 }
                },
                { "index": 1, "markdown": "## Positions", "images": [] }
              ],
              "model": "mistral-ocr-2505",
              "usage_info": { "pages_processed": 2, "doc_size_bytes": 1234 }
            }
            """;

    @Test
    void should_send_document_as_data_uri_and_join_pages() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://api.mistral.ai/v1")
                .apiKey("dummy")
                .modelName("mistral-ocr-latest")
                .tableFormat(MistralAiOcrTableFormat.HTML)
                .extractHeader(true)
                .build();

        // when
        Document document = model.parse("hello".getBytes(), "application/pdf");

        // then
        assertThat(document.text()).isEqualTo("# Invoice\n\n## Positions");
        assertThat(document.metadata().getString(MistralAiOcrModel.OCR_MODEL)).isEqualTo("mistral-ocr-2505");
        assertThat(document.metadata().getInteger(MistralAiOcrModel.PAGES_PROCESSED))
                .isEqualTo(2);
        assertThat(document.metadata().getInteger(MistralAiOcrModel.PAGE_COUNT)).isEqualTo(2);

        HttpRequest request = mockHttpClient.request();
        assertThat(request.url()).isEqualTo("https://api.mistral.ai/v1/ocr");
        assertThat(request.headers()).containsEntry("Authorization", List.of("Bearer dummy"));
        assertThat(request.body().replaceAll("\\s", ""))
                .contains("\"model\":\"mistral-ocr-latest\"")
                .contains("\"type\":\"document_url\"")
                .contains("\"document_url\":\"data:application/pdf;base64,aGVsbG8=\"")
                .contains("\"table_format\":\"html\"")
                .contains("\"extract_header\":true")
                .doesNotContain("extract_footer")
                .doesNotContain("image_url");
    }

    @Test
    void should_send_image_as_image_url() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-ocr-latest")
                .build();

        // when
        model.parse("hello".getBytes(), "image/png");

        // then
        assertThat(mockHttpClient.request().body().replaceAll("\\s", ""))
                .contains("\"type\":\"image_url\"")
                .contains("\"image_url\":\"data:image/png;base64,aGVsbG8=\"")
                .doesNotContain("document_url");
    }

    @Test
    void should_return_one_document_per_page() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-ocr-latest")
                .pages(List.of(0, 1))
                .build();

        // when
        List<Document> documents = model.parsePages("hello".getBytes(), "application/pdf");

        // then
        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).text()).isEqualTo("# Invoice");
        assertThat(documents.get(0).metadata().getInteger(MistralAiOcrModel.PAGE_INDEX))
                .isZero();
        assertThat(documents.get(1).metadata().getInteger(MistralAiOcrModel.PAGE_INDEX))
                .isEqualTo(1);
        assertThat(mockHttpClient.request().body().replaceAll("\\s", "")).contains("\"pages\":[0,1]");
    }

    @Test
    void should_support_azure_hosted_deployments_via_headers_and_query_params() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("https://my-resource.services.ai.azure.com/providers/mistral/azure")
                .apiKey("azure-key")
                .customHeaders(Map.of("api-key", "azure-key"))
                .customQueryParams(Map.of("api-version", "2024-05-01-preview"))
                .modelName("mistral-document-ai-2512")
                .build();

        // when
        model.parseDocumentUrl("https://example.com/contract.pdf");

        // then
        HttpRequest request = mockHttpClient.request();
        assertThat(request.url())
                .isEqualTo("https://my-resource.services.ai.azure.com/providers/mistral/azure/ocr"
                        + "?api-version=2024-05-01-preview");
        assertThat(request.headers()).containsEntry("api-key", List.of("azure-key"));
        assertThat(request.body().replaceAll("\\s", ""))
                .contains("\"model\":\"mistral-document-ai-2512\"")
                .contains("\"document_url\":\"https://example.com/contract.pdf\"");
    }

    /**
     * Enabling header or footer extraction makes the service move that text out of {@code markdown} into
     * its own field. Reading only {@code markdown} would therefore drop content, so both are folded back
     * into the text and additionally reported as metadata. The response below has the shape a deployment
     * returns with both options enabled, with the recognized text replaced by placeholders.
     */
    @Test
    void should_not_lose_extracted_header_and_footer() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body("""
                        {
                          "pages": [
                            {
                              "index": 0,
                              "markdown": "# Invoice 12345\\nAmount: 99.50 EUR",
                              "header": "Example Corp - Accounts Receivable",
                              "footer": "Page 1 of 1",
                              "images": [],
                              "hyperlinks": [],
                              "tables": []
                            }
                          ],
                          "model": "mistral-document-ai-2512",
                          "document_annotation": null,
                          "usage_info": { "pages_processed": 1, "doc_size_bytes": 19512 },
                          "content_filter_results": null
                        }
                        """).build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-document-ai-2512")
                .extractHeader(true)
                .extractFooter(true)
                .build();

        // when
        List<Document> pages = model.parsePages("hello".getBytes(), "application/pdf");

        // then
        assertThat(pages).hasSize(1);
        Document page = pages.get(0);
        assertThat(page.text())
                .contains("Example Corp - Accounts Receivable")
                .contains("Invoice 12345")
                .contains("Page 1 of 1");
        assertThat(page.metadata().getString(MistralAiOcrModel.PAGE_HEADER))
                .isEqualTo("Example Corp - Accounts Receivable");
        assertThat(page.metadata().getString(MistralAiOcrModel.PAGE_FOOTER)).isEqualTo("Page 1 of 1");
    }

    @Test
    void should_throw_when_nothing_was_recognized() {
        // given
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("{\"pages\": [{\"index\": 0, \"markdown\": \"\"}], \"model\": \"mistral-ocr-2505\"}")
                .build());

        MistralAiOcrModel model = MistralAiOcrModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-ocr-latest")
                .build();

        // when, then
        assertThatThrownBy(() -> model.parse("hello".getBytes(), "application/pdf"))
                .isInstanceOf(BlankDocumentException.class);
        assertThatThrownBy(() -> model.parsePages("hello".getBytes(), "application/pdf"))
                .isInstanceOf(BlankDocumentException.class);
    }
}
