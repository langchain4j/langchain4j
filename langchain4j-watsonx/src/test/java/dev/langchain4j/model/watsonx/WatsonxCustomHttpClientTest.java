package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.ibm.watsonx.ai.core.provider.HttpClientProvider;
import com.ibm.watsonx.ai.detection.detector.Pii;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.scoring.ScoringModel;
import java.net.http.HttpClient;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class WatsonxCustomHttpClientTest {

    @Test
    void should_use_custom_http_client_for_chat_model() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        WatsonxChatModel chatModel = WatsonxChatModel.builder()
                .baseUrl("https://localhost")
                .modelName("modelName")
                .apiKey("apiKey")
                .projectId("projectId")
                .httpClient(customClient)
                .build();

        Object chatService = getFieldValue(chatModel, "chatService");
        Object restclient = getFieldValue(chatService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));

        Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
        assertEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(asyncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_chat_model() {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                ChatModel chatModel = WatsonxChatModel.builder()
                        .baseUrl("https://localhost")
                        .modelName("modelId")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .verifySsl(verifySsl)
                        .build();

                Object chatService = getFieldValue(chatModel, "chatService");
                Object restclient = getFieldValue(chatService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

                Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
                assertNotEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(asyncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_streaming_chat_model() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl("https://localhost")
                .modelName("modelName")
                .apiKey("apiKey")
                .projectId("projectId")
                .httpClient(customClient)
                .build();

        Object chatService = getFieldValue(streamingChatModel, "chatService");
        Object restclient = getFieldValue(chatService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));

        Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
        assertEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(asyncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_streaming_chat_model() {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                        .baseUrl("https://localhost")
                        .modelName("modelId")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .verifySsl(verifySsl)
                        .build();

                Object chatService = getFieldValue(streamingChatModel, "chatService");
                Object restclient = getFieldValue(chatService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

                Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
                assertNotEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(asyncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_embedding_model() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
                .baseUrl("https://localhost")
                .modelName("modelName")
                .apiKey("apiKey")
                .projectId("projectId")
                .httpClient(customClient)
                .build();

        Object embeddingService = getFieldValue(embeddingModel, "embeddingService");
        Object restclient = getFieldValue(embeddingService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));
    }

    @Test
    void should_use_default_http_client_for_embedding_model() throws Exception {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                EmbeddingModel embeddingModel = WatsonxEmbeddingModel.builder()
                        .baseUrl("https://localhost")
                        .modelName("modelName")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .verifySsl(verifySsl)
                        .build();

                Object embeddingService = getFieldValue(embeddingModel, "embeddingService");
                Object restclient = getFieldValue(embeddingService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_moderation_model() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        ModerationModel moderationModel = WatsonxModerationModel.builder()
                .baseUrl("https://localhost")
                .apiKey("apiKey")
                .projectId("projectId")
                .detectors(Pii.ofDefaults())
                .httpClient(customClient)
                .build();

        Object detectionService = getFieldValue(moderationModel, "detectionService");
        Object restclient = getFieldValue(detectionService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_moderation_model() throws Exception {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                ModerationModel moderationModel = WatsonxModerationModel.builder()
                        .baseUrl("https://localhost")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .detectors(Pii.ofDefaults())
                        .verifySsl(verifySsl)
                        .build();

                Object detectionService = getFieldValue(moderationModel, "detectionService");
                Object restclient = getFieldValue(detectionService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_scoring_model() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        ScoringModel scoringModel = WatsonxScoringModel.builder()
                .baseUrl("https://localhost")
                .apiKey("apiKey")
                .projectId("projectId")
                .modelName("model-name")
                .httpClient(customClient)
                .build();

        Object rerankService = getFieldValue(scoringModel, "rerankService");
        Object restclient = getFieldValue(rerankService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_scoring_model() throws Exception {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                ScoringModel scoringModel = WatsonxScoringModel.builder()
                        .baseUrl("https://localhost")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .modelName("model-name")
                        .verifySsl(verifySsl)
                        .build();

                Object rerankService = getFieldValue(scoringModel, "rerankService");
                Object restclient = getFieldValue(rerankService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_token_count_estimator() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        TokenCountEstimator tokenCounterEstimator = WatsonxTokenCountEstimator.builder()
                .baseUrl("https://localhost")
                .modelName("modelName")
                .apiKey("apiKey")
                .projectId("projectId")
                .httpClient(customClient)
                .build();

        Object tokenizationService = getFieldValue(tokenCounterEstimator, "tokenizationService");
        Object restclient = getFieldValue(tokenizationService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));

        Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
        assertEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(asyncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(asyncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_token_count_estimator() throws Exception {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                TokenCountEstimator tokenCounterEstimator = WatsonxTokenCountEstimator.builder()
                        .baseUrl("https://localhost")
                        .modelName("modelName")
                        .apiKey("apiKey")
                        .projectId("projectId")
                        .verifySsl(verifySsl)
                        .build();

                Object tokenizationService = getFieldValue(tokenCounterEstimator, "tokenizationService");
                Object restclient = getFieldValue(tokenizationService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

                Object asyncHttpClient = getFieldValue(restclient, "asyncHttpClient");
                assertNotEquals(customClient, getFieldValue(asyncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(asyncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void should_use_custom_http_client_for_model_catalog() throws Exception {

        HttpClient customClient = HttpClient.newHttpClient();
        ModelCatalog modelCatalog = WatsonxModelCatalog.builder()
                .baseUrl("https://localhost")
                .httpClient(customClient)
                .build();

        Object foundationModelService = getFieldValue(modelCatalog, "foundationModelService");
        Object restclient = getFieldValue(foundationModelService, "client");
        assertEquals(customClient, getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(restclient, "httpClient"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(restclient, "httpClient"));

        Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
        assertEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(true), getFieldValue(syncHttpClient, "delegate"));
        assertNotEquals(HttpClientProvider.httpClient(false), getFieldValue(syncHttpClient, "delegate"));
    }

    @Test
    void should_use_default_http_client_for_model_catalog() throws Exception {

        Stream.of(true, false).forEach(verifySsl -> {
            try {

                HttpClient customClient = HttpClient.newHttpClient();
                ModelCatalog modelCatalog = WatsonxModelCatalog.builder()
                        .baseUrl("https://localhost")
                        .verifySsl(verifySsl)
                        .build();

                Object foundationModelService = getFieldValue(modelCatalog, "foundationModelService");
                Object restclient = getFieldValue(foundationModelService, "client");
                assertNotEquals(customClient, getFieldValue(restclient, "httpClient"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(restclient, "httpClient"));

                Object syncHttpClient = getFieldValue(restclient, "syncHttpClient");
                assertNotEquals(customClient, getFieldValue(syncHttpClient, "delegate"));
                assertEquals(HttpClientProvider.httpClient(verifySsl), getFieldValue(syncHttpClient, "delegate"));

            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @SuppressWarnings("null")
    public static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        NoSuchFieldException lastException = null;

        while (clazz != null) {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                lastException = e;
                clazz = clazz.getSuperclass();
            }
        }
        throw lastException;
    }
}
