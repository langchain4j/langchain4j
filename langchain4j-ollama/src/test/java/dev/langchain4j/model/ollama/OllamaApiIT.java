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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OllamaApiIT {

    private static MockWebServer mockWebServer;

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    @BeforeAll
    public static void init() throws IOException{
        mockWebServer = new MockWebServer();
        Dispatcher dispatcher = new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
                MockResponse mockResponse = new MockResponse();
                if ("/api/chat".equals(recordedRequest.getPath()) || "/additional/api/chat".equals(recordedRequest.getPath())) {
                    ChatResponse chatResponse = ChatResponse.builder()
                            .done(true)
                            .message(Message.builder().content(recordedRequest.getRequestUrl() != null ? recordedRequest.getRequestUrl().toString().replace("127.0.0.1", "localhost") : null).build())
                            .build();
                    return mockResponse.setResponseCode(200).setBody(GSON.toJson(chatResponse));
                } else {
                    return mockResponse.setResponseCode(404);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        mockWebServer.start();
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
        assertTrue(chatResponse.getDone());
        assertEquals("http://localhost:" + mockWebServer.getPort() + "/api/chat", chatResponse.getMessage().getContent());
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
        assertTrue(chatResponse.getDone());
        assertEquals("http://localhost:" + mockWebServer.getPort() + "/api/chat", chatResponse.getMessage().getContent());
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
        assertTrue(chatResponse.getDone());
        assertEquals("http://localhost:" + mockWebServer.getPort() + "/additional/api/chat", chatResponse.getMessage().getContent());
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
        assertTrue(chatResponse.getDone());
        assertEquals("http://localhost:" + mockWebServer.getPort() + "/additional/api/chat", chatResponse.getMessage().getContent());
    }

    @AfterAll
    public static void afterAll() throws IOException {
        mockWebServer.close();
    }
}
