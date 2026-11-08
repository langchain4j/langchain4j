package dev.langchain4j.http.client.log;

import static dev.langchain4j.http.client.log.HttpRequestLogger.format;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.slf4j.Logger;

@Internal
class HttpResponseLogger {

    static void log(Logger log, SuccessfulHttpResponse response) {
        try {
            log.info(
                    """
                            HTTP response:
                            - status code: {}
                            - headers: {}
                            - body: {}
                            """,
                    response.statusCode(),
                    format(response.headers()),
                    formatBody(response));
        } catch (Exception e) {
            log.warn("Exception occurred while logging HTTP response: {}", e.getMessage());
        }
    }

    /**
     * Avoids decoding and dumping binary responses (e.g. audio) as text.
     */
    private static Object formatBody(SuccessfulHttpResponse response) {
        String contentType = response.contentType();
        if (isTextual(contentType)) {
            return response.body();
        }
        byte[] bytes = response.bodyBytes();
        int length = bytes == null ? 0 : bytes.length;
        return "[binary body, " + length + " bytes, content-type: " + contentType + "]";
    }

    private static boolean isTextual(String contentType) {
        if (contentType == null) {
            // Unknown content type: preserve the previous behavior and log the body as text.
            return true;
        }
        String type = contentType.toLowerCase();
        return type.contains("json")
                || type.contains("text")
                || type.contains("xml")
                || type.contains("html")
                || type.contains("x-www-form-urlencoded")
                || type.contains("event-stream");
    }
}
