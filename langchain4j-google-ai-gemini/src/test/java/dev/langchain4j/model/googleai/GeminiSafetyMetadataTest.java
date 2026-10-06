package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the safety data Gemini reports on the response side. All fixtures are raw API payloads fed through the
 * HTTP client, so that deserialization is exercised end-to-end rather than bypassed.
 */
class GeminiSafetyMetadataTest {

    private static final String MODEL_NAME = "gemini-2.5-flash";

    private static final String WITH_CANDIDATE_SAFETY_RATINGS = """
            {
              "responseId": "response-id-123",
              "modelVersion": "gemini-2.5-flash",
              "candidates": [
                {
                  "content": {"role": "model", "parts": [{"text": "Hello"}]},
                  "finishReason": "SAFETY",
                  "safetyRatings": [
                    {"category": "HARM_CATEGORY_HARASSMENT", "probability": "HIGH", "blocked": true},
                    {"category": "HARM_CATEGORY_HATE_SPEECH", "probability": "NEGLIGIBLE"}
                  ]
                }
              ],
              "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 20, "totalTokenCount": 30}
            }
            """;

    private static final String BLOCKED_PROMPT = """
            {
              "responseId": "response-id-123",
              "modelVersion": "gemini-2.5-flash",
              "promptFeedback": {
                "blockReason": "PROHIBITED_CONTENT",
                "safetyRatings": [
                  {"category": "HARM_CATEGORY_HARASSMENT", "probability": "HIGH", "blocked": true}
                ]
              },
              "usageMetadata": {"promptTokenCount": 10, "totalTokenCount": 10}
            }
            """;

    private static final String WITHOUT_SAFETY_DATA = """
            {
              "responseId": "response-id-123",
              "modelVersion": "gemini-2.5-flash",
              "candidates": [
                {
                  "content": {"role": "model", "parts": [{"text": "Hello"}]},
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 20, "totalTokenCount": 30}
            }
            """;

    @Nested
    class Sync {

        @Test
        void should_expose_safety_ratings_of_the_generated_content() {
            ChatResponse chatResponse = chat(WITH_CANDIDATE_SAFETY_RATINGS);

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chatResponse);
            assertThat(metadata.safetyRatings())
                    .containsExactly(
                            new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "HIGH", true),
                            new GeminiSafetyRating("HARM_CATEGORY_HATE_SPEECH", "NEGLIGIBLE", null));
            assertThat(metadata.promptSafetyRatings()).isEmpty();
            assertThat(metadata.blockReason()).isNull();
            assertThat(metadata.finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
        }

        @Test
        void should_expose_block_reason_and_prompt_safety_ratings_when_prompt_is_blocked() {
            ChatResponse chatResponse = chat(BLOCKED_PROMPT);

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chatResponse);
            assertThat(metadata.blockReason()).isEqualTo("PROHIBITED_CONTENT");
            assertThat(metadata.promptSafetyRatings())
                    .containsExactly(new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "HIGH", true));
            assertThat(metadata.safetyRatings()).isEmpty();
            assertThat(metadata.finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
            assertThat(chatResponse.aiMessage().text()).isNull();
            assertThat(metadata.tokenUsage().inputTokenCount()).isEqualTo(10);
        }

        @Test
        void should_expose_empty_safety_data_when_response_carries_none() {
            ChatResponse chatResponse = chat(WITHOUT_SAFETY_DATA);

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chatResponse);
            assertThat(metadata.safetyRatings()).isEmpty();
            assertThat(metadata.promptSafetyRatings()).isEmpty();
            assertThat(metadata.blockReason()).isNull();
            assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello");
        }

        @Test
        void should_not_fail_on_harm_categories_langchain4j_does_not_know() {
            // Gemini keeps adding harm categories (HARM_CATEGORY_JAILBREAK, the legacy PaLM ones, ...).
            // An unrecognised one must not take down the whole response.
            String body = """
                    {
                      "responseId": "response-id-123",
                      "modelVersion": "gemini-2.5-flash",
                      "candidates": [
                        {
                          "content": {"role": "model", "parts": [{"text": "Hello"}]},
                          "finishReason": "STOP",
                          "safetyRatings": [
                            {"category": "HARM_CATEGORY_JAILBREAK", "probability": "NEGLIGIBLE"},
                            {"category": "SOMETHING_INVENTED_LATER", "probability": "LOW"}
                          ]
                        }
                      ],
                      "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 20, "totalTokenCount": 30}
                    }
                    """;

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chat(body));
            assertThat(metadata.safetyRatings())
                    .extracting(GeminiSafetyRating::category)
                    .containsExactly("HARM_CATEGORY_JAILBREAK", "SOMETHING_INVENTED_LATER");
        }

        private ChatResponse chat(String responseBody) {
            GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                    .httpClientBuilder(new MockHttpClientBuilder(respondWith(responseBody)))
                    .baseUrl("http://localhost")
                    .apiKey("does not matter")
                    .modelName(MODEL_NAME)
                    .maxRetries(0)
                    .build();

            return model.chat(
                    ChatRequest.builder().messages(UserMessage.from("Hi")).build());
        }
    }

    @Nested
    class Streaming {

        @Test
        void should_expose_safety_ratings_of_the_generated_content() {
            ChatResponse chatResponse = stream(WITH_CANDIDATE_SAFETY_RATINGS);

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chatResponse);
            assertThat(metadata.safetyRatings())
                    .containsExactly(
                            new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "HIGH", true),
                            new GeminiSafetyRating("HARM_CATEGORY_HATE_SPEECH", "NEGLIGIBLE", null));
            assertThat(metadata.promptSafetyRatings()).isEmpty();
            assertThat(metadata.blockReason()).isNull();
        }

        @Test
        void should_expose_block_reason_and_prompt_safety_ratings_when_prompt_is_blocked() {
            ChatResponse chatResponse = stream(BLOCKED_PROMPT);

            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(chatResponse);
            assertThat(metadata.blockReason()).isEqualTo("PROHIBITED_CONTENT");
            assertThat(metadata.promptSafetyRatings())
                    .containsExactly(new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "HIGH", true));
            assertThat(metadata.safetyRatings()).isEmpty();
            assertThat(metadata.finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
            assertThat(chatResponse.aiMessage().text()).isNull();
        }

        @Test
        void should_expose_empty_safety_data_when_response_carries_none() {
            GoogleAiGeminiChatResponseMetadata metadata = metadataOf(stream(WITHOUT_SAFETY_DATA));

            assertThat(metadata.safetyRatings()).isEmpty();
            assertThat(metadata.promptSafetyRatings()).isEmpty();
            assertThat(metadata.blockReason()).isNull();
        }

        private ChatResponse stream(String eventBody) {
            MockHttpClient mockHttpClient =
                    MockHttpClient.thatAlwaysResponds(List.of(new ServerSentEvent("message", eventBody)));

            GoogleAiGeminiStreamingChatModel model = GoogleAiGeminiStreamingChatModel.builder()
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .baseUrl("http://localhost")
                    .apiKey("does not matter")
                    .modelName(MODEL_NAME)
                    .build();

            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            model.chat(
                    ChatRequest.builder().messages(UserMessage.from("Hi")).build(), new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {}

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            future.complete(completeResponse);
                        }

                        @Override
                        public void onError(Throwable error) {
                            future.completeExceptionally(error);
                        }
                    });

            try {
                return future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    class Batch {

        private static final String SUCCEEDED_BATCH_WITH_SAFETY_RATINGS = """
                {
                  "name": "batches/abc",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                    "model": "models/gemini-2.5-flash",
                    "state": "BATCH_STATE_SUCCEEDED",
                    "name": "batches/abc"
                  },
                  "done": true,
                  "response": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                    "inlinedResponses": {
                      "inlinedResponses": [
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {"parts": [{"text": "Paris"}], "role": "model"},
                                "finishReason": "STOP",
                                "safetyRatings": [
                                  {"category": "HARM_CATEGORY_HARASSMENT", "probability": "NEGLIGIBLE"}
                                ]
                              }
                            ],
                            "usageMetadata": {"promptTokenCount": 8, "candidatesTokenCount": 8, "totalTokenCount": 16},
                            "modelVersion": "gemini-2.5-flash"
                          }
                        },
                        {
                          "response": {
                            "promptFeedback": {
                              "blockReason": "BLOCKLIST",
                              "safetyRatings": [
                                {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "probability": "HIGH",
                                 "blocked": true}
                              ]
                            },
                            "modelVersion": "gemini-2.5-flash"
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        @Test
        void should_expose_safety_data_for_batch_results() {
            MockHttpClient mockHttpClient = respondWith(SUCCEEDED_BATCH_WITH_SAFETY_RATINGS);
            GoogleAiGeminiBatchChatModel model = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey("does not matter")
                    .modelName(MODEL_NAME)
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            List<ChatResponse> responses = model.retrieve("batches/abc").responses();

            assertThat(responses).hasSize(2);

            GoogleAiGeminiChatResponseMetadata succeeded = metadataOf(responses.get(0));
            assertThat(succeeded.safetyRatings())
                    .containsExactly(new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "NEGLIGIBLE", null));
            assertThat(succeeded.blockReason()).isNull();

            GoogleAiGeminiChatResponseMetadata blocked = metadataOf(responses.get(1));
            assertThat(blocked.blockReason()).isEqualTo("BLOCKLIST");
            assertThat(blocked.promptSafetyRatings())
                    .containsExactly(new GeminiSafetyRating("HARM_CATEGORY_DANGEROUS_CONTENT", "HIGH", true));
            assertThat(blocked.safetyRatings()).isEmpty();
            assertThat(blocked.finishReason()).isEqualTo(FinishReason.CONTENT_FILTER);
        }
    }

    @Test
    void safety_ratings_should_be_unmodifiable() {
        GoogleAiGeminiChatResponseMetadata metadata = GoogleAiGeminiChatResponseMetadata.builder()
                .safetyRatings(
                        new ArrayList<>(List.of(new GeminiSafetyRating("HARM_CATEGORY_HARASSMENT", "LOW", false))))
                .build();

        assertThat(metadata.safetyRatings()).hasSize(1);
        assertThatThrownBy(() -> metadata.safetyRatings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static MockHttpClient respondWith(String body) {
        return MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().body(body).statusCode(200).build());
    }

    private static GoogleAiGeminiChatResponseMetadata metadataOf(ChatResponse chatResponse) {
        return (GoogleAiGeminiChatResponseMetadata) chatResponse.metadata();
    }
}
