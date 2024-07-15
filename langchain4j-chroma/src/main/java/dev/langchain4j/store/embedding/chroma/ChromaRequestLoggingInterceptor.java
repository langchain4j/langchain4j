package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.store.embedding.chroma.ChromaResponseLoggingInterceptor.getHeaders;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChromaRequestLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(ChromaRequestLoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        this.log(request);
        return chain.proceed(request);
    }

    private void log(Request request) {
        try {
            log.debug(
                "Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                request.method(),
                request.url(),
                getHeaders(request.headers()),
                getBody(request)
            );
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
