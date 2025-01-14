package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.langchain4j.http.HttpRequestLogger.format;

@Experimental
class HttpResponseLogger {

    private static final Logger log = LoggerFactory.getLogger(HttpResponseLogger.class);

    static void log(HttpResponse httpResponse) {
        try {
            // TODO pretty print body?
            log.debug("Response:\n- status code: {}\n- headers: {}\n- body: {}",
                    httpResponse.statusCode(), format(httpResponse.headers()), getBody(httpResponse));
        } catch (Exception e) {
            log.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private static String getBody(HttpResponse httpResponse) {
        return isEventStream(httpResponse)
                ? "[skipping response body due to streaming]"
                : httpResponse.body();
    }

    private static boolean isEventStream(HttpResponse httpResponse) {
        String contentType = httpResponse.headers().get("Content-Type");
        if (contentType != null) {
            return contentType.contains("event-stream");
        }

        contentType = httpResponse.headers().get("content-type");
        if (contentType != null) {
            return contentType.contains("event-stream");
        }

        return false;
    }
}
