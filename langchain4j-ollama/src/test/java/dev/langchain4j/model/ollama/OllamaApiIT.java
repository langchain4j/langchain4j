package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OllamaApiIT {

    private static MockWebServer mockWebServer;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void init() throws IOException {
        mockWebServer = new MockWebServer();
        Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {

                Message message = Message.builder()
                        .content(recordedRequest.getRequestUrl().toString())
                        .build();

                OllamaChatResponse ollamaChatResponse =
                        OllamaChatResponse.builder().message(message).build();

                String jsonBody;
                try {
                    jsonBody = OBJECT_MAPPER.writeValueAsString(ollamaChatResponse);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

                return new MockResponse().setResponseCode(200).setBody(jsonBody);
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        mockWebServer.start();
    }

    @AfterAll
    static void afterAll() throws IOException {
        mockWebServer.close();
    }

    @Test
    void base_url_with_trailing_slash_without_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort() + "/")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        OllamaChatResponse ollamaChatResponse =
                ollamaClient.chat(OllamaChatRequest.builder().build());

        assertThat(ollamaChatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/api/chat");
    }

    @Test
    void base_url_without_trailing_slash_without_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort())
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        OllamaChatResponse ollamaChatResponse =
                ollamaClient.chat(OllamaChatRequest.builder().build());

        assertThat(ollamaChatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/api/chat");
    }

    @Test
    void base_url_with_trailing_slash_with_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort() + "/additional/")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        OllamaChatResponse ollamaChatResponse =
                ollamaClient.chat(OllamaChatRequest.builder().build());

        assertThat(ollamaChatResponse.getMessage().getContent())
                .endsWith(mockWebServer.getPort() + "/additional/api/chat");
    }

    @Test
    void base_url_without_trailing_slash_with_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort() + "/additional")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        OllamaChatResponse ollamaChatResponse =
                ollamaClient.chat(OllamaChatRequest.builder().build());

        assertThat(ollamaChatResponse.getMessage().getContent())
                .endsWith(mockWebServer.getPort() + "/additional/api/chat");
    }
}
