package dev.langchain4j.model.openaiofficial;

import com.openai.core.RequestOptions;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Test helper: {@link HttpClient} decorator that records the serialized JSON body of every outgoing request.
 */
public class OpenAiOfficialSpyingHttpClient implements HttpClient {

    private final HttpClient delegate;
    private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());

    public OpenAiOfficialSpyingHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    public List<String> requestBodies() {
        return requestBodies;
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
        return delegate.execute(captureBody(request), requestOptions);
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions requestOptions) {
        return delegate.executeAsync(captureBody(request), requestOptions);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private HttpRequest captureBody(HttpRequest request) {
        HttpRequestBody body = request.body();
        if (body == null) {
            return request;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            body.writeTo(buffer);
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Failed to capture request body", e));
        }
        byte[] bytes = buffer.toByteArray();
        requestBodies.add(new String(bytes, StandardCharsets.UTF_8));
        return request.toBuilder()
                .body(new ByteArrayRequestBody(bytes, body.contentType()))
                .build();
    }

    private static final class ByteArrayRequestBody implements HttpRequestBody {

        private final byte[] bytes;
        private final String contentType;

        ByteArrayRequestBody(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        @Override
        public void writeTo(OutputStream outputStream) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public boolean repeatable() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
