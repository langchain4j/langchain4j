package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OllamaStreamingChatModelToolCallsTest {

    private static final String DONE_EVENT_DATA =
            "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,"
                    + "\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1}";

    private final List<CompleteToolCall> completeToolCalls = new ArrayList<>();

    @Test
    void should_map_every_tool_call_of_one_message_when_the_index_is_absent() throws Exception {
        ChatResponse response = chat(toolCallsEvent(
                "{\"function\":{\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}",
                "{\"function\":{\"name\":\"getTime\",\"arguments\":{\"state\":\"Texas\"}}}"));

        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(
                        tuple("getWeather", "{\"state\":\"California\"}"), tuple("getTime", "{\"state\":\"Texas\"}"));
    }

    @Test
    void should_map_every_tool_call_of_one_message_when_they_share_the_same_index() throws Exception {
        ChatResponse response = chat(toolCallsEvent(
                "{\"function\":{\"index\":0,\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}",
                "{\"function\":{\"index\":0,\"name\":\"getTime\",\"arguments\":{\"state\":\"Texas\"}}}"));

        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::name)
                .containsExactly("getWeather", "getTime");
    }

    @Test
    void should_map_repeated_calls_to_the_same_tool_in_one_message() throws Exception {
        ChatResponse response = chat(toolCallsEvent(
                "{\"function\":{\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}",
                "{\"function\":{\"name\":\"getWeather\",\"arguments\":{\"state\":\"Texas\"}}}"));

        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::arguments)
                .containsExactly("{\"state\":\"California\"}", "{\"state\":\"Texas\"}");
    }

    @Test
    void should_report_one_complete_tool_call_per_tool_call_of_one_message() throws Exception {
        chat(toolCallsEvent(
                "{\"function\":{\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}",
                "{\"function\":{\"name\":\"getTime\",\"arguments\":{\"state\":\"Texas\"}}}"));

        assertThat(completeToolCalls)
                .extracting(completeToolCall ->
                        completeToolCall.toolExecutionRequest().name())
                .containsExactly("getWeather", "getTime");
    }

    @Test
    void should_keep_mapping_tool_calls_that_arrive_in_separate_messages() throws Exception {
        ChatResponse response = chat(
                toolCallsEvent(
                        "{\"function\":{\"index\":0,\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}"),
                toolCallsEvent(
                        "{\"function\":{\"index\":1,\"name\":\"getTime\",\"arguments\":{\"state\":\"Texas\"}}}"));

        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::name)
                .containsExactly("getWeather", "getTime");
    }

    @Test
    void should_map_a_single_tool_call_to_a_single_request() throws Exception {
        ChatResponse response = chat(
                toolCallsEvent("{\"function\":{\"name\":\"getWeather\",\"arguments\":{\"state\":\"California\"}}}"));

        assertThat(response.aiMessage().toolExecutionRequests())
                .extracting(ToolExecutionRequest::name, ToolExecutionRequest::arguments)
                .containsExactly(tuple("getWeather", "{\"state\":\"California\"}"));
    }

    private static ServerSentEvent toolCallsEvent(String... toolCalls) {
        return new ServerSentEvent(
                null,
                "{\"model\":\"llama3\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":["
                        + String.join(",", toolCalls) + "]},\"done\":false}");
    }

    private ChatResponse chat(ServerSentEvent... events) throws Exception {
        List<ServerSentEvent> allEvents = new ArrayList<>(List.of(events));
        allEvents.add(new ServerSentEvent(null, DONE_EVENT_DATA));

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(allEvents)))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat("hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

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
        });

        return future.get(5, TimeUnit.SECONDS);
    }
}
