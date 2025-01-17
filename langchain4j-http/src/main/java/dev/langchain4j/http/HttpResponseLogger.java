package dev.langchain4j.http;

import dev.langchain4j.Experimental;
import org.slf4j.Logger;

import static dev.langchain4j.http.HttpRequestLogger.format;

@Experimental
class HttpResponseLogger {

    static void log(Logger log, HttpResponse httpResponse) {
        try {
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
