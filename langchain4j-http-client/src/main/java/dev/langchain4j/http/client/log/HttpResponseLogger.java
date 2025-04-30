package dev.langchain4j.http.client.log;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.slf4j.Logger;

import static dev.langchain4j.http.client.log.HttpRequestLogger.format;

@Internal
class HttpResponseLogger {

    static void log(Logger log, SuccessfulHttpResponse response) {
        try {
            log.debug("""
                            HTTP response:
                            - status code: {}
                            - headers: {}
                            - body: {}
                            """,
                    response.statusCode(), format(response.headers()), response.body());
        } catch (Exception e) {
            log.warn("Exception occurred while logging HTTP response: {}", e.getMessage());
        }
    }
}
