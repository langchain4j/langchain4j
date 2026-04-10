package dev.langchain4j.service.tool;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerToolInvocationLimitStreamingTest {

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    private final List<String> searchInvocations = new ArrayList<>();
    private final List<String> calcInvocations = new ArrayList<>();

    @BeforeEach
    void setUp() {
        searchInvocations.clear();
        calcInvocations.clear();
    }

    class SearchTool {
        @Tool
        String search(String query) {
            searchInvocations.add(query);
            return "result for: " + query;
        }
    }

    class CalculatorTool {
        @Tool
        String calculate(String expression) {
            calcInvocations.add(expression);
            return "42";
        }
    }

    private ChatResponse streamToCompletion(TokenStream tokenStream) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        tokenStream
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        return future.get(30, SECONDS);
    }

    @Test
    void should_enforce_per_tool_limit_with_streaming() throws Exception {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Round 1: search executed (count=1)
        // Round 2: search executed (count=2, at limit)
        // After round 2: search exhausted, removed. No tools left.
        // Round 3: model called with no tools, returns text.
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(searchRequest), AiMessage.from(searchRequest), AiMessage.from("Streaming done"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool())
                .maxToolInvocations("search", 2)
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("search"));

        assertThat(searchInvocations).hasSize(2);
        assertThat(response.aiMessage().text()).isEqualTo("Streaming done");
    }

    @Test
    void should_throw_exception_with_error_behavior_streaming() throws Exception {
        ToolExecutionRequest search1 = ToolExecutionRequest.builder()
                .id("1")
                .name("search")
                .arguments("{\"arg0\":\"q1\"}")
                .build();
        ToolExecutionRequest search2 = ToolExecutionRequest.builder()
                .id("2")
                .name("search")
                .arguments("{\"arg0\":\"q2\"}")
                .build();

        // Single response with 2 parallel search calls, limit=1 with ERROR
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from(search1, search2));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool())
                .maxToolInvocations("search", 1, ToolLimitExceededBehavior.ERROR)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        assistant
                .chat("search")
                .onCompleteResponse(
                        r -> futureError.completeExceptionally(new IllegalStateException("should not complete")))
                .onError(futureError::complete)
                .start();

        Throwable error = futureError.get(30, SECONDS);
        assertThat(error).isInstanceOf(ToolInvocationLimitExceededException.class);
    }

    @Test
    void should_halt_loop_with_end_behavior_streaming() throws Exception {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Round 1: search executed (count=1, at limit)
        // Round 2: search over-budget → END fires
        // Round 3: final no-tools call → text answer
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(searchRequest), AiMessage.from(searchRequest), AiMessage.from("END streaming answer"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool())
                .maxToolInvocations("search", 1, ToolLimitExceededBehavior.END)
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("search"));

        assertThat(searchInvocations).hasSize(1);
        assertThat(response.aiMessage().text()).isEqualTo("END streaming answer");
    }

    @Test
    void should_not_affect_streaming_when_no_limits_configured() throws Exception {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(searchRequest), AiMessage.from(searchRequest), AiMessage.from("Done streaming"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool())
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("search"));

        assertThat(searchInvocations).hasSize(2);
        assertThat(response.aiMessage().text()).isEqualTo("Done streaming");
    }

    @Test
    void should_reject_parallel_over_budget_calls_streaming() throws Exception {
        ToolExecutionRequest search1 = ToolExecutionRequest.builder()
                .id("1")
                .name("search")
                .arguments("{\"arg0\":\"q1\"}")
                .build();
        ToolExecutionRequest search2 = ToolExecutionRequest.builder()
                .id("2")
                .name("search")
                .arguments("{\"arg0\":\"q2\"}")
                .build();
        ToolExecutionRequest search3 = ToolExecutionRequest.builder()
                .id("3")
                .name("search")
                .arguments("{\"arg0\":\"q3\"}")
                .build();

        // 3 parallel search calls in one response, limit=2
        // First 2 execute, 3rd rejected. Then no tools left, final call returns text.
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(search1, search2, search3), AiMessage.from("Parallel done"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool())
                .maxToolInvocations("search", 2)
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("search"));

        assertThat(searchInvocations).hasSize(2);
        assertThat(response.aiMessage().text()).isEqualTo("Parallel done");
    }

    @Test
    void should_not_execute_sibling_tools_when_end_fires_streaming() throws Exception {
        ToolExecutionRequest calcRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .id("2")
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Round 1: calculate executes (count=1, at limit with END)
        // Round 2: model returns [search, calculate] — within-budget tool BEFORE END-triggering tool
        //   Both are classified first: search is within budget, calculate triggers END
        //   Neither should execute (END skips all tools in the batch)
        // Round 3: final no-tools call
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(calcRequest),
                AiMessage.from(searchRequest, calcRequest),
                AiMessage.from("END streaming"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool(), new CalculatorTool())
                .maxToolInvocations("calculate", 1, ToolLimitExceededBehavior.END)
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("do things"));

        assertThat(calcInvocations).hasSize(1);
        assertThat(searchInvocations).isEmpty();
        assertThat(response.aiMessage().text()).isEqualTo("END streaming");
    }

    @Test
    void should_override_global_default_with_per_tool_limit_streaming() throws Exception {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();
        ToolExecutionRequest calcRequest = ToolExecutionRequest.builder()
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();

        // search override=3, calculate uses global default=1
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(searchRequest),
                AiMessage.from(calcRequest),
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest),
                AiMessage.from("Override done"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(new SearchTool(), new CalculatorTool())
                .maxToolInvocations(1)
                .maxToolInvocations("search", 3)
                .build();

        ChatResponse response = streamToCompletion(assistant.chat("do things"));

        assertThat(searchInvocations).hasSize(3);
        assertThat(calcInvocations).hasSize(1);
        assertThat(response.aiMessage().text()).isEqualTo("Override done");
    }
}
