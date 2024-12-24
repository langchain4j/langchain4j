package dev.langchain4j.model.azure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class AzureOpenAiAiServicesStreamingWithJsonSchemaIT {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiAiServicesStreamingWithJsonSchemaIT.class);

    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                AzureOpenAiStreamingChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(true)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build(),
                AzureOpenAiStreamingChatModel.builder()
                        .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                        .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                        .deploymentName("gpt-4o-mini")
                        .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequestsAndResponses(true)
                        .build()
        );
    }

    static class Person {
        @JsonProperty
        String name;
        @JsonProperty
        int age;
        @JsonProperty
        Double height;
        @JsonProperty
        boolean married;
    }

    @Test
    protected void should_extract_streaming_pojo() {

        for (StreamingChatLanguageModel model : models()) {
            // given
            String text = "Klaus is 37 years old, 1.78m height and single";

            // when
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addIntegerProperty("age")
                                            .addNumberProperty("height")
                                            .addBooleanProperty("married")
                                            .required("name", "age", "height", "married")
                                            .build())
                                    .build())
                            .build())
                    .build();

            CompletableFuture<String> futureAnswer = new CompletableFuture<>();
            CompletableFuture<AiMessage> futureResponse = new CompletableFuture<>();

            model.chat(chatRequest, new StreamingChatResponseHandler() {

                private final StringBuilder answerBuilder = new StringBuilder();

                @Override
                public void onPartialResponse(final String partialResponse) {
                    log.debug("Partial response: {}", partialResponse);
                    answerBuilder.append(partialResponse);
                }

                @Override
                public void onCompleteResponse(final ChatResponse completeResponse) {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(completeResponse.aiMessage());
                }

                @Override
                public void onError(final Throwable error) {
                    futureAnswer.completeExceptionally(error);
                    futureResponse.completeExceptionally(error);
                }
            });
            String answer = futureAnswer.join();
            log.info("Answer: {}", answer);

            ObjectMapper objectMapper = new ObjectMapper();
            try {

                Person person = objectMapper.readValue(answer, Person.class);

                assertThat(person.name).isEqualTo("Klaus");
                assertThat(person.age).isEqualTo(37);
                assertThat(person.height).isEqualTo(1.78);
                assertThat(person.married).isFalse();
            } catch (JsonProcessingException e) {
                fail("Failed to deserialize JSON: {} with Exception: {}", answer, e);
            }
        }
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
