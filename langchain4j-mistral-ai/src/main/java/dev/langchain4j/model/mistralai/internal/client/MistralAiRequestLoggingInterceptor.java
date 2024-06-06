package dev.langchain4j.model.mistralai.internal.client;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
class MistralAiRequestLoggingInterceptor implements Interceptor {

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

    static String getHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false).map(header -> {
            String headerKey = header.component1();
            String headerValue = header.component2();
            if (headerKey.equals("Authorization")) {
                headerValue = maskAuthorizationHeaderValue(headerValue);
            }
            return String.format("[%s: %s]", headerKey, headerValue);
        }).collect(Collectors.joining(", "));
    }

    private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
        try {
            Pattern apiKeyBearerPattern = Pattern.compile("^(Bearer\\s*) ([A-Za-z0-9]{1,32})$");

            Matcher matcher = apiKeyBearerPattern.matcher(authorizationHeaderValue);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String bearer = matcher.group(1);
                String token = matcher.group(2);
                matcher.appendReplacement(sb, bearer + " " + token.substring(0, 2) + "..." + token.substring(token.length() - 2));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return "Error while masking Authorization header value";
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
