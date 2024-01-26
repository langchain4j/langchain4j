package dev.langchain4j.model.qianfan.client;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RequestLoggingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private static final Pattern BEARER_PATTERN = Pattern.compile("(Bearer\\s*sk-)(\\w{2})(\\w+)(\\w{2})");

    public RequestLoggingInterceptor() {
    }

    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        log(request);
        return chain.proceed(request);
    }

    private static void log(Request request) {
        try {
            log.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}", new Object[]{request.method(), request.url(), inOneLine(request.headers()), getBody(request)});
        } catch (Exception var2) {
            log.warn("Failed to log request", var2);
        }

    }

    static String inOneLine(Headers headers) {
        return (String) StreamSupport.stream(headers.spliterator(), false).map((header) -> {
            String headerKey = (String)header.component1();
            String headerValue = (String)header.component2();
            if (headerKey.equals("Authorization")) {
                headerValue = maskAuthorizationHeaderValue(headerValue);
            } else if (headerKey.equals("api-key")) {
                headerValue = maskApiKeyHeaderValue(headerValue);
            }

            return String.format("[%s: %s]", headerKey, headerValue);
        }).collect(Collectors.joining(", "));
    }

    private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
        try {
            Matcher matcher = BEARER_PATTERN.matcher(authorizationHeaderValue);
            StringBuffer sb = new StringBuffer();

            while(matcher.find()) {
                matcher.appendReplacement(sb, matcher.group(1) + matcher.group(2) + "..." + matcher.group(4));
            }

            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception var3) {
            return "Failed to mask the API key.";
        }
    }

    private static String maskApiKeyHeaderValue(String apiKeyHeaderValue) {
        try {
            return apiKeyHeaderValue.length() <= 4 ? apiKeyHeaderValue : apiKeyHeaderValue.substring(0, 2) + "..." + apiKeyHeaderValue.substring(apiKeyHeaderValue.length() - 2);
        } catch (Exception var2) {
            return "Failed to mask the API key.";
        }
    }

    private static String getBody(Request request) {
        if("GET".equals(request.method())){
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception var2) {
            log.warn("Exception happened while reading request body", var2);
            return "[Exception happened while reading request body. Check logs for more details.]";
        }
    }
}
