package dev.langchain4j.model.mistralai;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static dev.langchain4j.model.mistralai.DefaultMistralAiHelper.getHeaders;

class MistralAiRequestLoggingInterceptor implements Interceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MistralAiRequestLoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        this.log(request);
        return chain.proceed(request);
    }

    private void log(Request request) {
        try {
            LOGGER.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(), request.url(), getHeaders(request.headers()), getBody(request));
        } catch (Exception e) {
            LOGGER.warn("Error while logging request: {}", e.getMessage());
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
            LOGGER.warn("Exception while getting body", e);
            return "Exception while getting body: " + e.getMessage();
        }
    }
}
