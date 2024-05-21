package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

class GoogleSearchApiHttpRequestLoggingInterceptor implements HttpExecuteInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSearchApiHttpRequestLoggingInterceptor.class);

    @Override
    public void intercept(HttpRequest httpRequest) {
        this.log(httpRequest);
    }

    private void log(HttpRequest httpRequest) {
        try {
            LOGGER.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    httpRequest.getRequestMethod(), httpRequest.getUrl(), getHeaders(httpRequest.getHeaders()), getBody(httpRequest.getContent()));
        } catch (Exception e) {
            LOGGER.warn("Error while logging request: {}", e.getMessage());
        }
    }

    private static String getHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
                .map(entry -> String.format("[%s: %s]", entry.getKey(), entry.getValue())).collect(Collectors.joining(", "));
    }

    private static String getBody(HttpContent content) {
        try {
            if (content == null) {
                return "";
            }
            return content.toString();
        } catch (Exception e) {
            LOGGER.warn("Exception while getting body", e);
            return "Exception while getting body: " + e.getMessage();
        }
    }
}
