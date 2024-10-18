package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

class GeminiService {
    private static final String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";
    private static final String API_KEY_HEADER_NAME = "x-goog-api-key";

    private final HttpClient httpClient;
    private final Gson gson;
    private final Logger logger;

    public GeminiService(Logger logger, Duration timeout) {
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public GeminiGenerateContentResponse generateContent(String modelName, String apiKey, GeminiGenerateContentRequest request) throws IOException {
        String url = String.format("%s/models/%s:generateContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiGenerateContentResponse.class);
    }

    public GeminiCountTokensResponse countTokens(String modelName, String apiKey, GeminiCountTokensRequest request) throws IOException {
        String url = String.format("%s/models/%s:countTokens", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GeminiCountTokensResponse.class);
    }

    public GoogleAiEmbeddingResponse embed(String modelName, String apiKey, GoogleAiEmbeddingRequest request) throws IOException {
        String url = String.format("%s/models/%s:embedContent", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiEmbeddingResponse.class);
    }

    public GoogleAiBatchEmbeddingResponse batchEmbed(String modelName, String apiKey, GoogleAiBatchEmbeddingRequest request) throws IOException {
        String url = String.format("%s/models/%s:batchEmbedContents", GEMINI_AI_ENDPOINT, modelName);
        return sendRequest(url, apiKey, request, GoogleAiBatchEmbeddingResponse.class);
    }

    private <T> T sendRequest(String url, String apiKey, Object requestBody, Class<T> responseType) throws IOException {
        String jsonBody = gson.toJson(requestBody);
        HttpRequest request = buildHttpRequest(url, apiKey, jsonBody);

        logRequest(jsonBody);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                throw new IOException(String.format("HTTP error (%d): %s", response.statusCode(), response.body()));
            }

            logResponse(response.body());

            return gson.fromJson(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sending request was interrupted", e);
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