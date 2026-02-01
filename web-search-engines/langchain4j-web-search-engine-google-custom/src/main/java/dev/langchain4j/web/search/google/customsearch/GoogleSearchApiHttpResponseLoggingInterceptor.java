package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

class GoogleSearchApiHttpResponseLoggingInterceptor implements HttpResponseInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSearchApiHttpResponseLoggingInterceptor.class);

    @Override
    public void interceptResponse(HttpResponse httpResponse) {
        this.log(httpResponse);
    }

    private void log(HttpResponse httpResponse) {
        try {
            httpResponse.getRequest().setParser(new GsonFactory().createJsonObjectParser());
            LOGGER.debug("Response:\n- status code: {}\n- headers: {}",
                    httpResponse.getStatusCode(), getHeaders(httpResponse.getHeaders())); // response body can't be got twice by google token constraints, it'll be logged in GoogleCustomSearchApiClient
        } catch (Exception e) {
            LOGGER.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private static String getHeaders(HttpHeaders headers) {
        return headers.entrySet().stream()
                .map(entry -> String.format("[%s: %s]", entry.getKey(), entry.getValue())).collect(Collectors.joining(", "));
    }
}
