package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamingToolResultCorrelationTest {

    interface Assistant {
        TokenStream chat(String message);
    }

    static class Tools {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool
        String lookup(@P(name = "city") String city, @P(name = "days") int days) {
            calls.add(city);
            if (days < 0) {
                throw new IllegalArgumentException("days must be positive");
            }
            return city + ":" + days;
        }
    }

    @ParameterizedTest
    @CsvSource({
        "true,false,unique", "true,true,unique", "false,false,unique", "false,true,unique",
        "true,false,missing", "true,true,missing", "true,false,duplicate", "true,true,duplicate"
    })
    void should_correlate_results_when_final_arguments_are_reformatted(
            boolean concurrent, boolean toolFails, String ids) throws Exception {
        Tools tools = new Tools();
        String secondDays = toolFails ? "-1" : "2";
        String firstId = ids.equals("missing") ? null : "first";
        String secondId = ids.equals("unique") ? "second" : firstId;
        List<ToolExecutionRequest> streamed = List.of(
                request(firstId, "{\"city\": \"Paris\", \"days\": 1}"),
                request(secondId, "{\"city\": \"Rome\", \"days\": " + secondDays + "}"));
        List<ToolExecutionRequest> completed = List.of(
                request(firstId, "{\"days\":1,\"city\":\"Paris\"}"),
                request(secondId, "{\"days\":" + secondDays + ",\"city\":\"Rome\"}"));
        List<ChatRequest> modelRequests = new ArrayList<>();
        StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                modelRequests.add(request);
                if (modelRequests.size() == 1) {
                    for (int i = 0; i < streamed.size(); i++) {
                        handler.onCompleteToolCall(new CompleteToolCall(i, streamed.get(i)));
                    }
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
        var builder =
                AiServices.builder(Assistant.class).streamingChatModel(model).tools(tools);
        // Complete the second tool first, without timing-dependent sleeps.
        if (concurrent) {
            List<Runnable> pending = new ArrayList<>();
            builder.executeToolsConcurrently(task -> {
                pending.add(task);
                if (pending.size() == 2) {
                    pending.get(1).run();
                    pending.get(0).run();
                }
            });
        }
        Assistant assistant = builder.build();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        assistant
                .chat("Look up Paris and Rome")
                .onCompleteResponse(response::complete)
                .onError(response::completeExceptionally)
                .start();

        assertThat(response.get(5, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("done");
        assertThat(tools.calls)
                .containsExactlyElementsOf(concurrent ? List.of("Rome", "Paris") : List.of("Paris", "Rome"));
        assertThat(modelRequests).hasSize(2);
        List<ToolExecutionResultMessage> results = modelRequests.get(1).messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .toList();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(firstId);
        assertThat(results.get(0).toolName()).isEqualTo("lookup");
        assertThat(results.get(0).text()).isEqualTo("Paris:1");
        assertThat(results.get(1).id()).isEqualTo(secondId);
        assertThat(results.get(1).toolName()).isEqualTo("lookup");
        assertThat(results.get(1).text()).isEqualTo(toolFails ? "days must be positive" : "Rome:2");
    }

    private static ToolExecutionRequest request(String id, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name("lookup")
                .arguments(arguments)
                .build();
    }
}
