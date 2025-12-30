package dev.langchain4j.data.document.loader.confluence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConfluenceDocumentLoaderTest {

    @Test
    void shouldLoadTwoDocumentsAndStripHtml() {
        HttpClient httpClient = mock(HttpClient.class);
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(twoResultsResponseBody())
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.execute(requestCaptor.capture())).thenReturn(response);

        ConfluenceDocumentLoader loader = baseLoader(httpClient);

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(2);

        Document first = documents.get(0);
        assertThat(first.text()).isEqualTo("Content A");
        assertThat(first.metadata().getString(Document.URL))
                .isEqualTo("https://example.atlassian.net/wiki/spaces/DS/pages/1");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).url()).contains("/wiki/rest/api/content");
        assertThat(requests.get(0).url()).contains("start=0");
        assertThat(requests.get(0).url()).contains("limit=25");
        assertThat(requests.get(0).headers().get("Authorization"))
                .contains("Basic " + basicAuth("user@example.com", "token"));
    }

    @Test
    void shouldHandlePaginationAndFetchAllPages() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse firstPage = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(pagedResponse(1, 25, "/rest/api/content?start=25&limit=25"))
                .build();

        SuccessfulHttpResponse secondPage = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(pagedResponse(26, 2, null))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.execute(requestCaptor.capture())).thenReturn(firstPage, secondPage);

        ConfluenceDocumentLoader loader = baseLoader(httpClient);

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(27);
        assertThat(documents.get(0).text()).isEqualTo("Content 1");
        assertThat(documents.get(26).text()).isEqualTo("Content 27");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url()).contains("start=0");
        assertThat(requests.get(1).url()).contains("start=25");
    }

    @Test
    void shouldIncludeSpaceKeyInRequestAndMetadataWhenConfigured() {
        HttpClient httpClient = mock(HttpClient.class);
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(oneResultResponseBody())
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.execute(requestCaptor.capture())).thenReturn(response);

        ConfluenceDocumentLoader loader = ConfluenceDocumentLoader.builder()
                .baseUrl("https://example.atlassian.net/wiki")
                .username("user@example.com")
                .apiKey("token")
                .spaceKey("DS")
                .httpClient(httpClient)
                .build();

        List<Document> documents = loader.load();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).metadata().getString(ConfluenceDocumentLoader.METADATA_SPACE_KEY))
                .isEqualTo("DS");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).url()).contains("spaceKey=DS");
    }

    @Test
    void shouldThrowWhenApiReturnsNon2xxStatus() {
        HttpClient httpClient = mock(HttpClient.class);

        when(httpClient.execute(any(HttpRequest.class)))
                .thenThrow(new HttpException(401, "{\"message\":\"Unauthorized\"}"));

        ConfluenceDocumentLoader loader = baseLoader(httpClient);

        assertThatThrownBy(loader::load).isInstanceOf(RuntimeException.class).hasMessageContaining("status code 401");
    }

    @Test
    void shouldInterruptThreadWhenSendIsInterrupted() {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.execute(any(HttpRequest.class)))
                .thenThrow(new RuntimeException(new InterruptedException("boom")));

        ConfluenceDocumentLoader loader = baseLoader(httpClient);

        try {
            assertThatThrownBy(loader::load)
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldWrapIOExceptionFromHttpClient() {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.execute(any(HttpRequest.class))).thenThrow(new RuntimeException(new IOException("boom")));

        ConfluenceDocumentLoader loader = baseLoader(httpClient);

        assertThatThrownBy(loader::load)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to call Confluence API");
    }

    private static ConfluenceDocumentLoader baseLoader(HttpClient httpClient) {
        return ConfluenceDocumentLoader.builder()
                .baseUrl("https://example.atlassian.net/wiki")
                .username("user@example.com")
                .apiKey("token")
                .httpClient(httpClient)
                .build();
    }

    private static String twoResultsResponseBody() {
        return "{"
                + "\"results\":["
                + "{"
                + "\"id\":\"1\","
                + "\"title\":\"Page A\","
                + "\"body\":{\"storage\":{\"value\":\"<p>Content A</p>\"}},"
                + "\"_links\":{\"webui\":\"/spaces/DS/pages/1\"}"
                + "},"
                + "{"
                + "\"id\":\"2\","
                + "\"title\":\"Page B\","
                + "\"body\":{\"storage\":{\"value\":\"<p>Content B</p>\"}},"
                + "\"_links\":{\"webui\":\"/spaces/DS/pages/2\"}"
                + "}"
                + "],"
                + "\"_links\":{}"
                + "}";
    }

    private static String oneResultResponseBody() {
        return "{"
                + "\"results\":["
                + "{"
                + "\"id\":\"1\","
                + "\"title\":\"Page A\","
                + "\"body\":{\"storage\":{\"value\":\"<p>Content A</p>\"}},"
                + "\"_links\":{\"webui\":\"/spaces/DS/pages/1\"}"
                + "}"
                + "],"
                + "\"_links\":{}"
                + "}";
    }

    private static String pagedResponse(int firstId, int count, String nextLink) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"results\":[");
        for (int i = 0; i < count; i++) {
            int id = firstId + i;
            if (i > 0) {
                builder.append(',');
            }
            builder.append("{")
                    .append("\"id\":\"")
                    .append(id)
                    .append("\",")
                    .append("\"title\":\"Page ")
                    .append(id)
                    .append("\",")
                    .append("\"body\":{\"storage\":{\"value\":\"<p>Content ")
                    .append(id)
                    .append("</p>\"}},")
                    .append("\"_links\":{\"webui\":\"/spaces/DS/pages/")
                    .append(id)
                    .append("\"}")
                    .append("}");
        }
        builder.append("],\"_links\":{");
        if (nextLink != null) {
            builder.append("\"next\":\"").append(nextLink).append("\"");
        }
        builder.append("}}");
        return builder.toString();
    }

    private static String basicAuth(String username, String apiKey) {
        String raw = username + ":" + apiKey;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
