package dev.langchain4j.http.client.jdk;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MultipartBoundaryTest {
    @Test
    void should_use_a_distinct_boundary_for_each_multipart_body() {
        assertNotEquals(new MultipartBodyPublisher().contentType(), new MultipartBodyPublisher().contentType());
    }

    @Test
    void should_keep_the_content_type_boundary_in_sync_with_the_body() throws Exception {
        java.net.http.HttpRequest request = multipartRequest("hello");
        String boundary =
                request.headers().firstValue("Content-Type").orElseThrow().split("boundary=")[1];
        String body = body(request);

        assertTrue(body.startsWith("--" + boundary + "\r\n"));
        assertTrue(body.endsWith("\r\n--" + boundary + "--\r\n"));
    }

    @Test
    void should_not_reuse_the_known_boundary_embedded_in_uploaded_content() throws Exception {
        String content =
                "before\r\n------LangChain4j\r\n" + "Content-Disposition: form-data; name=\"example\"\r\n\r\nafter";
        java.net.http.HttpRequest request = multipartRequest(content);
        String boundary =
                request.headers().firstValue("Content-Type").orElseThrow().split("boundary=")[1];

        assertFalse(content.contains("--" + boundary));
        assertTrue(body(request).contains(content));
    }

    private static java.net.http.HttpRequest multipartRequest(String content) {
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("http://localhost/upload")
                .addFormDataFile("file", "multipart-example.txt", "text/plain", content.getBytes(UTF_8))
                .build();
        return JdkHttpClient.builder().build().toJdkRequest(request);
    }

    private static String body(java.net.http.HttpRequest request) throws Exception {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final ByteArrayOutputStream output = new ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(output.toByteArray());
            }
        });
        return new String(result.get(5, TimeUnit.SECONDS), UTF_8);
    }
}
