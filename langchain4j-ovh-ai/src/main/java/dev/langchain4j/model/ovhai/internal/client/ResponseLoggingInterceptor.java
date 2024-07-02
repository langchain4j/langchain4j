package dev.langchain4j.model.ovhai.internal.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static dev.langchain4j.model.ovhai.internal.client.RequestLoggingInterceptor.inOneLine;
import java.io.IOException;


class ResponseLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingInterceptor.class);

    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        log(response);
        return response;
    }

    void log(Response response) {
        log.debug(
                "Response:\n" +
                        "- status code: {}\n" +
                        "- headers: {}\n" +
                        "- body: {}",
                response.code(),
                inOneLine(response.headers()),
                getBody(response)
        );
    }

    private String getBody(Response response) {
        try {
            return response.peekBody(Long.MAX_VALUE).string();
        } catch (IOException e) {
            log.warn("Failed to log response", e);
            return "[failed to log response]";
        }
    }
}
