package dev.langchain4j.model.watsonx.internal.api;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static dev.langchain4j.model.watsonx.internal.api.WatsonxAiLoggingInterceptor.getHeaders;

public class WatsonxAiResponseLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(WatsonxAiResponseLoggingInterceptor.class);

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        this.log(response);
        return response;
    }

    private void log(Response response) {
        try {
            log.debug("Response:\n- status code: {}\n- headers: {}\n- body: {}",
                response.code(), getHeaders(response.headers()), this.getBody(response));
        } catch (Exception e) {
            log.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private String getBody(Response response) throws IOException {
        return isEventStream(response)
            ? "[skipping response body due to streaming]"
            : response.peekBody(Long.MAX_VALUE).string();
    }

    private static boolean isEventStream(Response response) {
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains("event-stream");
    }

}
