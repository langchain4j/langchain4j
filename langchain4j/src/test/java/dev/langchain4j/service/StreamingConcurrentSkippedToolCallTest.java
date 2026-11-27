package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreamingConcurrentSkippedToolCallTest {

    interface Assistant {
        TokenStream chat(String message);
    }

    static class Tools {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool
        String alpha(@P(name = "value") String value) {
            calls.add("alpha:" + value);
            return "alpha-result";
        }

        @Tool
        String beta(@P(name = "value") String value) {
            calls.add("beta:" + value);
            return "beta-result";
        }
    }

    @Test
    void should_execute_a_tool_call_that_never_received_onCompleteToolCall_exactly_once() throws Exception {
        ToolExecutionRequest streamedAlpha = request("1", "alpha", "{\"value\":\"from-stream\"}");
        ToolExecutionRequest finalAlpha = request("1", "alpha", "{\"value\":\"from-final\"}");
        ToolExecutionRequest finalBeta = request("2", "beta", "{\"value\":\"beta\"}");
        Tools tools = new Tools();
        List<ChatRequest> modelRequests = new ArrayList<>();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        chat(
                model(modelRequests, List.of(new CompleteToolCall(0, streamedAlpha)), List.of(finalAlpha, finalBeta)),
                tools,
                memory);

        assertThat(tools.calls).containsExactly("alpha:from-stream", "beta:beta");
        assertThat(resultMessages(modelRequests.get(1).messages()))
                .extracting(
                        ToolExecutionResultMessage::id,
                        ToolExecutionResultMessage::toolName,
                        ToolExecutionResultMessage::text)
                .containsExactly(tuple("1", "alpha", "alpha-result"), tuple("2", "beta", "beta-result"));
        AiMessage stored = memory.messages().stream()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filter(AiMessage::hasToolExecutionRequests)
                .findFirst()
                .orElseThrow();
        assertThat(stored.toolExecutionRequests()).containsExactly(finalAlpha, finalBeta);
        assertThat(resultMessages(memory.messages()))
                .extracting(ToolExecutionResultMessage::id)
                .containsExactly("1", "2");
    }

    @Test
    void should_not_execute_again_a_tool_already_started_when_the_final_arguments_differ() throws Exception {
        ToolExecutionRequest streamedAlpha = request("1", "alpha", "{\"value\":\"from-stream\"}");
        ToolExecutionRequest streamedBeta = request("2", "beta", "{\"value\":\"from-stream\"}");
        ToolExecutionRequest finalAlpha = request("1", "alpha", "{\"value\":\"from-final\"}");
        ToolExecutionRequest finalBeta = request("2", "beta", "{\"value\":\"from-final\"}");
        Tools tools = new Tools();
        List<ChatRequest> modelRequests = new ArrayList<>();

        chat(
                model(
                        modelRequests,
                        List.of(new CompleteToolCall(0, streamedAlpha), new CompleteToolCall(1, streamedBeta)),
                        List.of(finalAlpha, finalBeta)),
                tools,
                MessageWindowChatMemory.withMaxMessages(20));

        assertThat(tools.calls).containsExactly("alpha:from-stream", "beta:from-stream");
        assertThat(resultMessages(modelRequests.get(1).messages())).hasSize(2);
    }

    @Test
    void should_not_rerun_an_announced_tool_call_when_its_id_is_missing_and_arguments_were_reformatted()
            throws Exception {
        ToolExecutionRequest streamedAlpha = request(null, "alpha", "{\"value\":\"from-stream\"}");
        ToolExecutionRequest finalAlpha = request(null, "alpha", "{\"value\":\"from-final\"}");
        ToolExecutionRequest finalBeta = request(null, "beta", "{\"value\":\"beta\"}");
        Tools tools = new Tools();
        List<ChatRequest> modelRequests = new ArrayList<>();

        chat(
                model(modelRequests, List.of(new CompleteToolCall(0, streamedAlpha)), List.of(finalAlpha, finalBeta)),
                tools,
                MessageWindowChatMemory.withMaxMessages(20));

        assertThat(tools.calls).containsExactly("alpha:from-stream", "beta:beta");
        assertThat(resultMessages(modelRequests.get(1).messages())).hasSize(2);
    }

    @Test
    void should_execute_a_leading_tool_call_that_was_not_announced_without_rerunning_the_later_one() throws Exception {
        ToolExecutionRequest streamedBeta = request("2", "beta", "{\"value\":\"from-stream\"}");
        ToolExecutionRequest finalAlpha = request("1", "alpha", "{\"value\":\"alpha\"}");
        ToolExecutionRequest finalBeta = request("2", "beta", "{\"value\":\"from-final\"}");
        Tools tools = new Tools();
        List<ChatRequest> modelRequests = new ArrayList<>();

        chat(
                model(modelRequests, List.of(new CompleteToolCall(1, streamedBeta)), List.of(finalAlpha, finalBeta)),
                tools,
                MessageWindowChatMemory.withMaxMessages(20));

        assertThat(tools.calls).containsExactly("beta:from-stream", "alpha:alpha");
        assertThat(resultMessages(modelRequests.get(1).messages()))
                .extracting(ToolExecutionResultMessage::id, ToolExecutionResultMessage::text)
                .containsExactly(tuple("1", "alpha-result"), tuple("2", "beta-result"));
    }

    private static StreamingChatModel model(
            List<ChatRequest> modelRequests, List<CompleteToolCall> announced, List<ToolExecutionRequest> completed) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                modelRequests.add(request);
                if (modelRequests.size() == 1) {
                    announced.forEach(handler::onCompleteToolCall);
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from(completed))
                            .build());
                } else {
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("done"))
                            .build());
                }
            }
        };
    }

    private static void chat(StreamingChatModel model, Tools tools, ChatMemory memory) throws Exception {
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .tools(tools)
                .chatMemory(memory)
                .executeToolsConcurrently(Runnable::run)
                .build();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        assistant
                .chat("go")
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();
        assertThat(response.get(5, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("done");
    }

    private static List<ToolExecutionResultMessage> resultMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
    }

    private static ToolExecutionRequest request(String id, String name, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }
}
