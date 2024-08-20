package dev.langchain4j.model.ollama;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static org.assertj.core.api.Assertions.assertThat;

public class OllamaApiIT {

    private static MockWebServer mockWebServer;

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @BeforeAll
    public static void init() throws IOException {
        mockWebServer = new MockWebServer();
        Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {

                Message message = Message.builder()
                        .content(recordedRequest.getRequestUrl().toString())
                        .build();

                ChatResponse chatResponse = ChatResponse.builder()
                        .message(message)
                        .build();

                return new MockResponse()
                        .setResponseCode(200)
                        .setBody(GSON.toJson(chatResponse));
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        mockWebServer.start();
    }

    @AfterAll
    public static void afterAll() throws IOException {
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

        ChatResponse chatResponse = ollamaClient.chat(ChatRequest.builder().build());

        assertThat(chatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/api/chat");
    }

    @Test
    void base_url_without_trailing_slash_without_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort())
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatResponse chatResponse = ollamaClient.chat(ChatRequest.builder().build());

        assertThat(chatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/api/chat");

    }

    @Test
    void base_url_with_trailing_slash_with_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort() + "/additional/")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatResponse chatResponse = ollamaClient.chat(ChatRequest.builder().build());

        assertThat(chatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/additional/api/chat");
    }

    @Test
    void base_url_without_trailing_slash_with_addition_path() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl("http://localhost:" + mockWebServer.getPort() + "/additional")
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(5))
                .build();

        ChatResponse chatResponse = ollamaClient.chat(ChatRequest.builder().build());

        assertThat(chatResponse.getMessage().getContent()).endsWith(mockWebServer.getPort() + "/additional/api/chat");
    }
}
