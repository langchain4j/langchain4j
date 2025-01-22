package dev.langchain4j.http.client.log;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.slf4j.Logger;

import static dev.langchain4j.http.client.log.HttpRequestLogger.format;

@Experimental
class HttpResponseLogger {

    static void log(Logger log, SuccessfulHttpResponse response) {
        try {
            log.debug("Response:\n- status code: {}\n- headers: {}\n- body: {}",
                    response.statusCode(), format(response.headers()), getBody(response));
        } catch (Exception e) {
            log.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private static String getBody(SuccessfulHttpResponse response) {
        return isEventStream(response)
                ? "[skipping response body due to streaming]"
                : response.body();
    }

    private static boolean isEventStream(SuccessfulHttpResponse response) {
        String contentType = response.headers().get("Content-Type");
        if (contentType != null) {
            return contentType.contains("event-stream");
        }

        contentType = response.headers().get("content-type");
        if (contentType != null) {
            return contentType.contains("event-stream");
        }

        return false;
    }
}
