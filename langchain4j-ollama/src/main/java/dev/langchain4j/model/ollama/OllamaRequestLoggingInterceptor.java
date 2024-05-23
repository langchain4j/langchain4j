package dev.langchain4j.model.ollama;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

@Slf4j
class OllamaRequestLoggingInterceptor implements Interceptor {

    private static final Set<String> COMMON_SECRET_HEADERS =
            new HashSet<>(asList("authorization", "x-api-key", "x-auth-token"));

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        this.log(request);
        return chain.proceed(request);
    }

    private void log(Request request) {
        try {
            log.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(), request.url(), getHeaders(request.headers()), getBody(request));
        } catch (Exception e) {
            log.warn("Error while logging request: {}", e.getMessage());
        }
    }

    private static String getBody(Request request) {
        try {
            Buffer buffer = new Buffer();
            if (request.body() == null) {
                return "";
            }
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            log.warn("Exception while getting body", e);
            return "Exception while getting body: " + e.getMessage();
        }
    }

    private static String getHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(header -> formatHeader(header.component1(), header.component2()))
                .collect(joining(", "));
    }

    private static String formatHeader(String headerKey, String headerValue) {
        if (COMMON_SECRET_HEADERS.contains(headerKey.toLowerCase())) {
            headerValue = maskSecretKey(headerValue);
        }
        return String.format("[%s: %s]", headerKey, headerValue);
    }

    private static String maskSecretKey(String key) {
        if (isNullOrBlank(key)) {
            return key;
        }

        if (key.length() >= 7) {
            return key.substring(0, 5) + "..." + key.substring(key.length() - 2);
        } else {
            return "..."; // to short to be masked
        }
    }
}
