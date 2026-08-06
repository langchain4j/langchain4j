package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.watsonx.WatsonxGatewayChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxGatewayStreamingChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY_GATEWAY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_MODEL", matches = ".+")
public class WatsonxGatewayStreamingChatModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY_GATEWAY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_MODEL");

    @Test
    void should_do_streaming_chat() throws Exception {

        var streamingChatModel = createStreamingChatModel().build();

        var handler = new CollectingHandler();
        streamingChatModel.chat("What is the capital of Italy? Answer in one word.", handler);

        var response = handler.await();
        var text = response.aiMessage().text();

        assertNotNull(text);
        assertTrue(text.toLowerCase().contains("rome"));
        assertFalse(handler.partialResponses.isEmpty());
        assertEquals(text, String.join("", handler.partialResponses));
        assertEquals(FinishReason.STOP, response.finishReason());
        assertNotNull(response.id());
        assertNotNull(response.modelName());
        assertTrue(response.tokenUsage().outputTokenCount() > 0);
    }

    @Test
    void should_stream_a_tool_call() throws Exception {

        var weatherTool = ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the current weather of a city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        var streamingChatModel =
                createStreamingChatModel().toolSpecifications(weatherTool).build();

        var handler = new CollectingHandler();
        streamingChatModel.chat("What is the weather in Rome?", handler);

        var response = handler.await();
        var toolExecutionRequests = response.aiMessage().toolExecutionRequests();

        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());
        assertEquals(1, toolExecutionRequests.size());
        assertEquals("getWeather", toolExecutionRequests.get(0).name());
        assertTrue(toolExecutionRequests.get(0).arguments().toLowerCase().contains("rome"));
        assertFalse(handler.completeToolCalls.isEmpty());
        assertEquals(
                "getWeather",
                handler.completeToolCalls.get(0).toolExecutionRequest().name());
    }

    @Test
    void should_respect_stop_sequences() throws Exception {

        // A stop sequence is sent only when it is not empty, since the Model Gateway rejects an empty "stop" array.
        var streamingChatModel =
                createStreamingChatModel().stopSequences("Three").build();

        var handler = new CollectingHandler();
        streamingChatModel.chat(
                "Count from one to five. Write one capitalized word per line, without punctuation.", handler);

        var response = handler.await();
        var text = response.aiMessage().text();

        assertNotNull(text);
        assertFalse(text.toLowerCase().contains("four"));
        assertEquals(FinishReason.STOP, response.finishReason());
    }

    @Test
    void should_override_builder_defaults_with_per_request_parameters() throws Exception {

        var streamingChatModel = createStreamingChatModel().maxOutputTokens(200).build();

        var chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say hi."))
                .parameters(WatsonxGatewayChatRequestParameters.builder()
                        .maxOutputTokens(5)
                        .build())
                .build();

        var handler = new CollectingHandler();
        streamingChatModel.chat(chatRequest, handler);

        var response = handler.await();

        assertNotNull(response.aiMessage().text());
        assertNotNull(response.modelName());
        assertTrue(response.tokenUsage().outputTokenCount() <= 5);
    }

    // The temperature is left unset, since some models exposed by the gateway accept only their own default value.
    private WatsonxGatewayStreamingChatModel.Builder createStreamingChatModel() {
        return WatsonxGatewayStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(120));
    }

    static class CollectingHandler implements StreamingChatResponseHandler {

        final List<String> partialResponses = new ArrayList<>();
        final List<PartialToolCall> partialToolCalls = new ArrayList<>();
        final List<CompleteToolCall> completeToolCalls = new ArrayList<>();
        final CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            partialToolCalls.add(partialToolCall);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            completeToolCalls.add(completeToolCall);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            future.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            future.completeExceptionally(error);
        }

        ChatResponse await() throws Exception {
            return future.get(150, TimeUnit.SECONDS);
        }
    }
}
