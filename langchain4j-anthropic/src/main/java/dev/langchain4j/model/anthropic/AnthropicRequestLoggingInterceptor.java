package dev.langchain4j.model.anthropic;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.model.anthropic.AnthropicApi.X_API_KEY;

class AnthropicRequestLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(AnthropicRequestLoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        log(request);
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

    static String getHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false).map(header -> {
            String headerKey = header.component1();
            String headerValue = header.component2();
            if (headerKey.equals(X_API_KEY)) {
                headerValue = maskApiKey(headerValue);
            }
            return String.format("[%s: %s]", headerKey, headerValue);
        }).collect(Collectors.joining(", "));
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey.length() >= 7) {
            return apiKey.substring(0, 5) + "..." + apiKey.substring(apiKey.length() - 2);
        } else {
            return "API key is too short to be masked";
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
}
