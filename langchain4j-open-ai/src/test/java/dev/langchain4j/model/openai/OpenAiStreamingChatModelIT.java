package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.repeat;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_5_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.Map.entry;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelIT {

    @Test
    void should_respect_deprecated_maxTokens() throws Exception {

        // given
        int maxTokens = 1;

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxTokens(maxTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat("Tell me a long story", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(maxTokens);
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_respect_maxCompletionTokens() throws Exception {

        // given
        int maxCompletionTokens = 1;

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(maxCompletionTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat("Tell me a long story", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(maxCompletionTokens);
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_a_tool_with_blank_partial_arguments() throws JsonProcessingException {

        // given
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_5_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        ToolSpecification appendToFile = ToolSpecification.builder()
                .name("append_to_file")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("text")
                        .required("text")
                        .build())
                .build();

        String tenSpaces = repeat(" ", 10);

        UserMessage userMessage = UserMessage.from("Append to file the following text: '%s'".formatted(tenSpaces));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(appendToFile)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);

        // then
        AiMessage aiMessage = handler.get().aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("append_to_file");

        Map<String, Object> argumentsMap = new ObjectMapper().readValue(toolExecutionRequest.arguments(), Map.class);
        assertThat(argumentsMap).containsOnly(entry("text", tenSpaces));
    }

    @Test
    void should_stream_valid_json() throws JsonProcessingException {

        // given
        @JsonIgnoreProperties(ignoreUnknown = true) // to ignore the "joke" field
        record Person(String name, String surname) {}

        String responseFormat = "json_object";

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .responseFormat(responseFormat)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        Person person = new ObjectMapper().readValue(response.aiMessage().text(), Person.class);
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.surname).isEqualTo("Heisler");
    }

    @ParameterizedTest
    @EnumSource(
            value = OpenAiChatModelName.class,
            mode = EXCLUDE,
            names = {
                "GPT_4_32K", // don't have access
                "GPT_4_32K_0613", // don't have access
                "O1", // don't have access
                "O1_2024_12_17", // don't have access
                "O3", // don't have access
                "O3_2025_04_16", // don't have access
            })
    void should_support_all_model_names(OpenAiChatModelName modelName) {

        // given
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        String question = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(question, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");
    }

    @Test
    void should_set_custom_parameters_and_get_raw_response() throws JsonProcessingException {

        // given
        String city = "Munich";

        Map<String, Object> customParameters = Map.of("web_search_options", Map.of("user_location", new LinkedHashMap() {
                    {
                        put("type", "approximate");
                        put("approximate", Map.of("city", city));
                    }
                }
        ));

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Where can I buy good coffee?"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .customParameters(customParameters)
                        .build())
                .build();

        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("test-header", List.of("test-value")))
                .build();

        List<ServerSentEvent> events = List.of(
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"refusal\":null},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null,\"obfuscation\":\"QK00Z4Pe\"}"),
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"The\"},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null,\"obfuscation\":\"R9qgEHA\"}"),
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" capital\"},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null,\"obfuscation\":\"qL\"}"),
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" of\"},\"logprobs\":null,\"finish_reason\":null}],\"usage\":null,\"obfuscation\":\"45ArDuR\"}"),
                // skipped the rest, it does not matter
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[{\"index\":0,\"delta\":{},\"logprobs\":null,\"finish_reason\":\"stop\"}],\"usage\":null,\"obfuscation\":\"X0dZ\"}"),
                new ServerSentEvent(null, "{\"id\":\"chatcmpl-C9nlEVdwuKXDiM5yuGpixoJZCj4v5\",\"object\":\"chat.completion.chunk\",\"created\":1756452268,\"model\":\"gpt-4.1-nano-2025-04-14\",\"service_tier\":\"default\",\"system_fingerprint\":\"fp_e91a518ddb\",\"choices\":[],\"usage\":{\"prompt_tokens\":14,\"completion_tokens\":7,\"total_tokens\":21,\"prompt_tokens_details\":{\"cached_tokens\":0,\"audio_tokens\":0},\"completion_tokens_details\":{\"reasoning_tokens\":0,\"audio_tokens\":0,\"accepted_prediction_tokens\":0,\"rejected_prediction_tokens\":0}},\"obfuscation\":\"Rg6J79CMc7\"}"),
                new ServerSentEvent(null, "[DONE]")
        );

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(response, events);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(chatRequest, handler);
        ChatResponse chatResponse = handler.get();

        // then
        assertThat(mockHttpClient.request().body()).isEqualToIgnoringWhitespace("""
                {
                  "messages" : [ {
                    "role" : "user",
                    "content" : "Where can I buy good coffee?"
                  } ],
                  "stream" : true,
                  "stream_options" : {
                    "include_usage" : true
                  },
                  "web_search_options" : {
                    "user_location" : {
                      "type" : "approximate",
                      "approximate" : {
                        "city" : "Munich"
                      }
                    }
                  }
                }
                """);

        List<ServerSentEvent> rawEvents = ((OpenAiChatResponseMetadata) chatResponse.metadata()).rawServerSentEvents();
        assertThat(rawEvents).isEqualTo(events.subList(0, events.size() - 1)); // without [DONE]

        SuccessfulHttpResponse rawResponse = ((OpenAiChatResponseMetadata) chatResponse.metadata()).rawHttpResponse();
        assertThat(rawResponse).isEqualTo(response);
    }
}
