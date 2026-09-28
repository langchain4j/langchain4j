package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import mutiny.zero.ZeroPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The reactive AI Service starts every tool as soon as its {@link CompleteToolCall} is announced, then looks up
 * what it started for each tool call of the final response. Providers do not always report the same tool call
 * identically in both places, and a tool must still be executed exactly once.
 */
class StreamingPublisherToolResultCorrelationTest {

    interface Assistant {
        Flow.Publisher<String> chat(String message);
    }

    static class Tools {

        final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool
        String lookup(@P(name = "city") String city, @P(name = "days") int days) {
            calls.add(city);
            return city + ":" + days;
        }
    }

    /**
     * Announces tool calls with the arguments as streamed and reports them re-serialized (different key order
     * and spacing) in the final response, the way {@code BedrockStreamingChatModel} did.
     */
    static class ReformattingArgumentsModel implements StreamingChatModel {

        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        private final List<ToolExecutionRequest> announced;
        private final List<ToolExecutionRequest> reported;

        ReformattingArgumentsModel(List<ToolExecutionRequest> announced, List<ToolExecutionRequest> reported) {
            this.announced = announced;
            this.reported = reported;
        }

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            List<ChatModelStreamingEvent> events = new ArrayList<>();
            if (requests.size() == 1) {
                for (int i = 0; i < announced.size(); i++) {
                    events.add(new CompleteToolCall(i, announced.get(i)));
                }
                events.add(new CompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(reported))
                        .build()));
            } else {
                events.add(new CompleteResponse(
                        ChatResponse.builder().aiMessage(AiMessage.from("done")).build()));
            }
            return ZeroPublisher.fromIterable(events);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_execute_an_announced_tool_call_once(boolean announce) throws Exception {

        List<ToolExecutionRequest> announced =
                announce ? List.of(request("first", "{\"city\": \"Paris\", \"days\": 1}")) : List.of();
        List<ToolExecutionRequest> reported = List.of(request("first", "{\"days\":1,\"city\":\"Paris\"}"));

        Tools tools = new Tools();
        ReformattingArgumentsModel model = new ReformattingArgumentsModel(announced, reported);
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        await(assistant.chat("Look up Paris"));

        assertThat(tools.calls).containsExactly("Paris");
        assertThat(resultMessagesOfSecondRequest(model)).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo("first");
            assertThat(result.text()).isEqualTo("Paris:1");
        });
    }

    @Test
    void should_keep_tool_calls_apart_when_they_share_an_id() throws Exception {

        List<ToolExecutionRequest> announced = List.of(
                request("same", "{\"city\": \"Paris\", \"days\": 1}"), request("same", "{\"city\": \"Rome\", \"days\": 2}"));
        List<ToolExecutionRequest> reported = List.of(
                request("same", "{\"days\":1,\"city\":\"Paris\"}"), request("same", "{\"days\":2,\"city\":\"Rome\"}"));

        Tools tools = new Tools();
        ReformattingArgumentsModel model = new ReformattingArgumentsModel(announced, reported);
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .build();

        await(assistant.chat("Look up Paris and Rome"));

        // the two tools run concurrently, so only the number of executions is deterministic, not their order
        assertThat(tools.calls).containsExactlyInAnyOrder("Paris", "Rome");
        List<ToolExecutionResultMessage> results = resultMessagesOfSecondRequest(model);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).text()).isEqualTo("Paris:1");
        assertThat(results.get(1).text()).isEqualTo("Rome:2");
    }

    private static ToolExecutionRequest request(String id, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("lookup")
                .arguments(arguments)
                .build();
    }

    private static List<ToolExecutionResultMessage> resultMessagesOfSecondRequest(ReformattingArgumentsModel model) {
        assertThat(model.requests).hasSize(2);
        return model.requests.get(1).messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }

    private static void await(Flow.Publisher<String> publisher) throws Exception {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String partialResponse) {}

            @Override
            public void onError(Throwable error) {
                completion.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                completion.complete(null);
            }
        });
        completion.get(10, TimeUnit.SECONDS);
    }
}
