package dev.langchain4j.model.ollama;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

import java.io.IOException;

@Slf4j
class OllamaRequestLoggingInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        this.log(request);
        return chain.proceed(request);
    }

    private void log(Request request) {
        try {
            log.debug("Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(), request.url(), request.headers(), getBody(request));
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
}
