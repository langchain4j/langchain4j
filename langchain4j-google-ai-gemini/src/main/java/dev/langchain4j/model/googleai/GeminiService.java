package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class GeminiService {
    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Logger logger;

    GeminiService(Logger logger, Duration timeout) {
        this.logger = logger;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    GeminiGenerateContentResponse generateContent(String modelName, String apiKey, GeminiGenerateContentRequest request) {
        String url = String.format("%s/models/%s:generateContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    GeminiCountTokensResponse countTokens(String modelName, String apiKey, GeminiCountTokensRequest request) {
        String url = String.format("%s/models/%s:countTokens", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiCountTokensResponse.class);
    }

    GoogleAiEmbeddingResponse embed(String modelName, String apiKey, GoogleAiEmbeddingRequest request) {
        String url = String.format("%s/models/%s:embedContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiEmbeddingResponse.class);
    }

    GoogleAiBatchEmbeddingResponse batchEmbed(String modelName, String apiKey, GoogleAiBatchEmbeddingRequest request) {
        String url = String.format("%s/models/%s:batchEmbedContents", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiBatchEmbeddingResponse.class);
    }

    Stream<GeminiGenerateContentResponse> generateContentStream(String modelName, String apiKey, GeminiGenerateContentRequest request) {
        String url = String.format("%s/models/%s:streamGenerateContent?alt=sse", GEMINI_AI_ENDPOINT, modelName);
        return streamRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    private <T> T sendRequest(String url, String apiKey, Object requestBody, Class<T> responseType) {
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = buildHttpRequest(url, apiKey, jsonBody);

            logRequest(jsonBody);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw new RuntimeException(String.format("HTTP error (%d): %s", response.statusCode(), response.body()));
            }

            logResponse(response.body());

            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new RuntimeException("An error occurred while sending the request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sending the request was interrupted", e);
        }
    }

    private <T> Stream<T> streamRequest(String url, String apiKey, Object requestBody, Class<T> responseType) {
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest httpRequest = buildHttpRequest(url, apiKey, jsonBody);

            logRequest(jsonBody);

            HttpResponse<Stream<String>> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

            if (httpResponse.statusCode() >= 300) {
                String errorBody = httpResponse.body()
                        .collect(Collectors.joining("\n"));

                throw new RuntimeException(String.format("HTTP error (%d): %s", httpResponse.statusCode(), errorBody));
            }

            Stream<T> responseStream = httpResponse.body()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6)) // Remove "data: " prefix
                    .map(jsonString -> {
                        try {
                            return objectMapper.readValue(jsonString, responseType);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    });

            if (logger != null) {
                responseStream = responseStream.peek(response -> logger.debug("Partial response from Gemini:\n{}", response));
            }

            return responseStream;
        } catch (IOException e) {
            throw new RuntimeException("An error occurred while streaming the request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Streaming the request was interrupted", e);
        }
    }

    private HttpRequest buildHttpRequest(String url, String apiKey, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "LangChain4j")
                .header(API_KEY_HEADER_NAME, apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private void logRequest(String jsonBody) {
        if (logger != null) {
            logger.debug("Sending request to Gemini:\n{}", jsonBody);
        }
    }

    private void logResponse(String responseBody) {
        if (logger != null) {
            logger.debug("Response from Gemini:\n{}", responseBody);
        }
    }
}
