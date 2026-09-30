package dev.langchain4j.model.openaiofficial;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Test helper: an {@link HttpClient} that replays canned response bodies keyed by request path and records
 * every outgoing request, so the real OpenAI SDK performs the real serialization and parsing without a
 * network call or an API key.
 */
class OpenAiOfficialStubHttpClient implements HttpClient {

    private final Map<String, Deque<String>> responseBodiesByPath = new LinkedHashMap<>();
    private final List<RecordedRequest> recordedRequests = new ArrayList<>();

    void enqueue(String path, String responseBody) {
        responseBodiesByPath.computeIfAbsent(path, key -> new ArrayDeque<>()).add(responseBody);
    }

    List<RecordedRequest> recordedRequests() {
        return recordedRequests;
    }

    RecordedRequest requestTo(String path) {
        return recordedRequests.stream()
                .filter(recordedRequest -> recordedRequest.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No request was sent to '" + path + "'. Sent: "
                        + recordedRequests.stream().map(RecordedRequest::path).toList()));
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        String path = String.join("/", request.pathSegments());
        recordedRequests.add(new RecordedRequest(request.method().toString(), path, request.url(), readBody(request)));

        Deque<String> responseBodies = responseBodiesByPath.get(path);
        if (responseBodies == null || responseBodies.isEmpty()) {
            throw new AssertionError("No stubbed response for " + request.method() + " '" + path + "'");
        }
        return toResponse(responseBodies.poll());
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
        return CompletableFuture.completedFuture(execute(request, requestOptions));
    }

    @Override
    public void close() {}

    private static String readBody(HttpRequest request) {
        HttpRequestBody body = request.body();
        if (body == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            body.writeTo(buffer);
        } catch (Exception e) {
            throw new AssertionError("Failed to read the request body", e);
        }
        return buffer.toString(UTF_8);
    }

    private static HttpResponse toResponse(String responseBody) {
        byte[] bytes = responseBody.getBytes(UTF_8);
        return new HttpResponse() {

            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public Headers headers() {
                return Headers.builder().put("Content-Type", "application/json").build();
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public void close() {}
        };
    }

    record RecordedRequest(String method, String path, String url, String body) {

        String query() {
            int separator = url.indexOf('?');
            return separator < 0 ? "" : url.substring(separator + 1);
        }
    }
}
