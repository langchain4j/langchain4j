package dev.langchain4j.model.ollama.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OllamaAiServiceWithToolsIT extends AbstractOllamaToolsLanguageModelInfrastructure {

    ToolSpecification weatherToolSpecification = ToolSpecification.builder()
            .name("get_current_weather")
            .description("Get the current weather for a location")
            .parameters(JsonObjectSchema.builder()
                    .addEnumProperty("format", List.of("celsius", "fahrenheit"), "The format to return the weather in, e.g. 'celsius' or 'fahrenheit'")
                    .addStringProperty("location", "The location to get the weather for, e.g. San Francisco, CA")
                    .required("format", "location")
                    .build())
            .build();

    ToolSpecification toolWithoutParameter = ToolSpecification.builder()
            .name("get_current_time")
            .description("Get the current time")
            .build();

    ChatLanguageModel ollamaChatModel = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(LLAMA_3_1)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    OllamaStreamingChatModel ollamaStreamingChatModel = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(LLAMA_3_1)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(ollamaChatModel);
    }

    @Test
    void should_execute_a_tool_then_answer() {

        // given
        UserMessage userMessage = userMessage("What is the weather today in Paris?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(weatherToolSpecification)
                .build();

        // when
        ChatResponse response = ollamaChatModel.chat(chatRequest);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_weather");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"format\": \"celsius\", \"location\": \"Paris\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"format\": \"celsius\", \"location\": \"Paris\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        ChatResponse secondResponse = ollamaChatModel.chat(messages);

        // then
        AiMessage secondAiMessage = secondResponse.aiMessage();
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    void should_not_execute_a_tool_and_tell_a_joke() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(systemMessage("Use tools only if needed"), userMessage("Tell a joke"))
                .toolSpecifications(toolWithoutParameter)
                .build();

        // when
        ChatResponse response = ollamaChatModel.chat(chatRequest);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNotNull();
        assertThat(aiMessage.toolExecutionRequests()).isNull();
    }

    @Test
    void should_handle_tool_without_parameter() {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the current time?"))
                .toolSpecifications(toolWithoutParameter)
                .build();

        // when-then
        assertDoesNotThrow(() -> ollamaChatModel.chat(chatRequest));
    }

    @Test
    void should_handle_tools_call_in_streaming_scenario() throws Exception {
        // given
        UserMessage userMessage = userMessage("What is the weather today in Paris?");

        ChatRequest request = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(weatherToolSpecification)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        ollamaStreamingChatModel.chat(request, handler);

        ChatResponse response = handler.get();
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("get_current_weather");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"format\": \"celsius\", \"location\": \"Paris\"}");

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "{\"format\": \"celsius\", \"location\": \"Paris\", \"temperature\": \"32\"}");
        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        CompletableFuture<ChatResponse> secondFutureResponse = new CompletableFuture<>();

        AtomicInteger onPartialResponseCounter = new AtomicInteger(0);
        ollamaStreamingChatModel.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                onPartialResponseCounter.incrementAndGet();
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                secondFutureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        ChatResponse secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("32");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();
        assertThat(onPartialResponseCounter.get()).isPositive();
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_pojo_with_primitives(ChatLanguageModel model) {
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_pojo_with_nested_pojo(ChatLanguageModel model) {
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_list_of_strings_parameter(ChatLanguageModel model) {
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_list_of_POJOs_parameter(ChatLanguageModel model) {
    }

    @Test
    @Disabled("llama3.1 struggles with this test scenario")
    @Override
    protected void should_execute_tool_with_collection_of_integers_parameter(ChatLanguageModel model) {
    }
}
