package dev.langchain4j.model.anthropic.internal.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicFile;
import dev.langchain4j.model.anthropic.internal.api.AnthropicFileDeleteResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicFilesListResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnthropicFilesClientTest {

    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_BASE_URL = "https://api.anthropic.com/v1";
    private static final String TEST_VERSION = "2023-06-01";

    @Nested
    class UploadTest {

        @Test
        void shouldSendMultipartUploadRequest() {
            AnthropicFile expectedFile = anthropicFile();
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedFile))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            AnthropicFilesClient subject = createClient(mockHttpClient);

            AnthropicFile actualFile =
                    subject.upload("report.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));

            assertThat(actualFile).isEqualTo(expectedFile);

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/files");
            assertThat(sentRequest.headers().get("x-api-key")).containsExactly(TEST_API_KEY);
            assertThat(sentRequest.headers().get("anthropic-version")).containsExactly(TEST_VERSION);
            assertThat(sentRequest.headers().get("anthropic-beta")).containsExactly("files-api-2025-04-14");
            assertThat(sentRequest.formDataFiles()).containsKey("file");
        }
    }

    @Nested
    class RetrieveTest {

        @Test
        void shouldSendRetrieveRequest() {
            AnthropicFile expectedFile = anthropicFile();
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedFile))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            AnthropicFilesClient subject = createClient(mockHttpClient);

            AnthropicFile actualFile = subject.retrieve("file_abc123");

            assertThat(actualFile).isEqualTo(expectedFile);

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/files/file_abc123");
            assertThat(sentRequest.headers().get("anthropic-beta")).containsExactly("files-api-2025-04-14");
        }
    }

    @Nested
    class ListTest {

        @Test
        void shouldSendListRequest() {
            AnthropicFilesListResponse expectedResponse = new AnthropicFilesListResponse();
            expectedResponse.data = java.util.List.of(anthropicFile());
            expectedResponse.hasMore = false;
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            AnthropicFilesClient subject = createClient(mockHttpClient);

            AnthropicFilesListResponse actualResponse = subject.list();

            assertThat(actualResponse).isEqualTo(expectedResponse);

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/files");
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void shouldSendDeleteRequest() {
            AnthropicFileDeleteResponse expectedResponse = new AnthropicFileDeleteResponse();
            expectedResponse.id = "file_abc123";
            expectedResponse.type = "file_deleted";
            SuccessfulHttpResponse httpResponse = SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(Json.toJson(expectedResponse))
                    .build();
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(httpResponse);
            AnthropicFilesClient subject = createClient(mockHttpClient);

            AnthropicFileDeleteResponse actualResponse = subject.delete("file_abc123");

            assertThat(actualResponse).isEqualTo(expectedResponse);

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.DELETE);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/files/file_abc123");
        }
    }

    @Nested
    class DownloadTest {

        @Test
        void shouldThrowUnsupportedOperationException() {
            AnthropicFilesClient subject = createClient(MockHttpClient.thatAlwaysResponds(
                    SuccessfulHttpResponse.builder().statusCode(200).body("").build()));

            assertThatThrownBy(() -> subject.download("file_abc123")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static AnthropicFilesClient createClient(MockHttpClient mockHttpClient) {
        return AnthropicFilesClient.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey(TEST_API_KEY)
                .baseUrl(TEST_BASE_URL)
                .version(TEST_VERSION)
                .build();
    }

    private static AnthropicFile anthropicFile() {
        AnthropicFile file = new AnthropicFile();
        file.id = "file_abc123";
        file.type = "file";
        file.filename = "report.pdf";
        file.mimeType = "application/pdf";
        file.sizeBytes = 1024L;
        file.createdAt = "2026-01-01T00:00:00Z";
        file.downloadable = true;
        return file;
    }
}
