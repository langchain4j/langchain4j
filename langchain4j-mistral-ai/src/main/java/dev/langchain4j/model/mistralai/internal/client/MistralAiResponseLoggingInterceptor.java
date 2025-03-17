package dev.langchain4j.model.mistralai.internal.client;

import static dev.langchain4j.model.mistralai.internal.client.MistralAiRequestLoggingInterceptor.getHeaders;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MistralAiResponseLoggingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(MistralAiResponseLoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        this.log(response);
        return response;
    }

    private void log(Response response) {
        try {
            log.debug(
                    "Response:\n- status code: {}\n- headers: {}\n- body: {}",
                    response.code(),
                    getHeaders(response.headers()),
                    this.getBody(response));
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
