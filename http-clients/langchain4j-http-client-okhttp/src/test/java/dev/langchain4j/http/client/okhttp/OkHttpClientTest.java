package dev.langchain4j.http.client.okhttp;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OkHttpClientTest {

    @Test
    void should_send_repeated_form_data_fields() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/upload", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            byte[] response = "ok".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            HttpRequest request = HttpRequest.builder()
                    .method(POST)
                    .url("http://localhost:" + server.getAddress().getPort() + "/upload")
                    .addFormDataField("timestamp_granularities[]", "word")
                    .addFormDataField("timestamp_granularities[]", "segment")
                    .build();

            SuccessfulHttpResponse response = OkHttpClient.builder().build().execute(request);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(requestBody.get()).contains("word").contains("segment");
            assertThat(numberOfOccurrences(requestBody.get(), "name=\"timestamp_granularities[]\""))
                    .isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    private static int numberOfOccurrences(String value, String substring) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = value.indexOf(substring, fromIndex)) >= 0) {
            count++;
            fromIndex += substring.length();
        }
        return count;
    }
}
