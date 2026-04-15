package dev.langchain4j.service.tool;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerToolExecutionLimitTest {

    interface Assistant {
        Result<String> chat(String message);
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

    /**
     * Creates a ChatModel that returns queued AiMessages in order,
     * falling back to a text response when the queue is exhausted.
     */
    private static ChatModel sequentialModel(AiMessage... messages) {
        Queue<AiMessage> queue = new ConcurrentLinkedQueue<>(asList(messages));
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                AiMessage next = queue.poll();
                if (next == null) {
                    next = AiMessage.from("Fallback response");
                }
                return ChatResponse.builder()
                        .aiMessage(next)
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };
    }

    @Test
    void should_enforce_per_tool_limit_with_continue_behavior() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"query1\"}")
                .build();

        // Round 1: search executed (count=1)
        // Round 2: search executed (count=2, now at limit)
        // After round 2: search removed from effectiveTools, all tools exhausted, loop ends
        ChatModel model = sequentialModel(AiMessage.from(searchRequest), AiMessage.from(searchRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().maxExecutions("search", 2).build())
                .build();

        Result<String> result = assistant.chat("search for something");

        assertThat(searchInvocations).hasSize(2);
    }

    @Test
    void should_enforce_global_default_limit() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"query1\"}")
                .build();

        // Round 1: search executed (count=1, at limit)
        // After round 1: search removed, all tools exhausted, loop ends
        ChatModel model = sequentialModel(AiMessage.from(searchRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().defaultLimit(1).build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(searchInvocations).hasSize(1);
    }

    @Test
    void should_override_global_default_with_per_tool_limit() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();
        ToolExecutionRequest calcRequest = ToolExecutionRequest.builder()
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();

        // search has override limit=3, calculate uses global default=1
        // Round 1: search (count=1)
        // Round 2: calc (count=1, at limit) → calc removed
        // Round 3: search (count=2)
        // Round 4: search (count=3, at limit) → search removed → all tools exhausted, loop ends
        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest),
                AiMessage.from(calcRequest),
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .defaultLimit(1)
                        .maxExecutions("search", 3)
                        .build())
                .build();

        Result<String> result = assistant.chat("do things");

        assertThat(searchInvocations).hasSize(3);
        assertThat(calcInvocations).hasSize(1);
    }

    @Test
    void should_throw_exception_with_error_behavior() {
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

        // Single response with 2 parallel search calls, limit=1 with ERROR behavior
        // First call executes (count=1), second triggers ERROR
        ChatModel model = sequentialModel(AiMessage.from(search1, search2));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("search", 1, ToolLimitExceededBehavior.ERROR)
                        .build())
                .build();

        assertThatThrownBy(() -> assistant.chat("search"))
                .isInstanceOf(ToolExecutionLimitExceededException.class)
                .hasMessageContaining("search")
                .hasMessageContaining("1");
    }

    @Test
    void should_halt_loop_with_end_behavior() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Round 1: search executed (count=1, at limit)
        // Round 2: search over-budget → END fires
        // Round 3 (final): LLM called with no tools, produces text answer
        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest),
                AiMessage.from("END produced this answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("search", 1, ToolLimitExceededBehavior.END)
                        .build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(searchInvocations).hasSize(1);
        assertThat(result.content()).isEqualTo("END produced this answer");
    }

    @Test
    void should_reject_parallel_over_budget_calls_in_same_response() {
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

        // Single response with 3 parallel search calls, limit=2
        // First 2 execute, 3rd rejected with error message
        // After this round: search exhausted, removed. But calculator remains, so loop continues.
        // Next response: model gives text answer.
        ChatModel model = sequentialModel(AiMessage.from(search1, search2, search3), AiMessage.from("Done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().maxExecutions("search", 2).build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(result.content()).isEqualTo("Done");
        assertThat(searchInvocations).hasSize(2);
    }

    @Test
    void should_handle_zero_limit() {
        // With zero limit, search tool should be removed from effectiveTools
        // before the very first LLM call. But the initial ChatRequest is built
        // before the loop runs, so the tool IS present in the first call.
        // The zero-limit removal happens inside the loop after tool execution.
        //
        // Actually, a zero-limit tool will be in the initial request. If the model
        // calls it, the call is rejected (count 0 >= limit 0). Then it's removed.
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest), // rejected (0 >= 0), then removed
                AiMessage.from("No tools left"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().maxExecutions("search", 0).build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(result.content()).isEqualTo("No tools left");
        assertThat(searchInvocations).isEmpty();
    }

    @Test
    void should_not_affect_behavior_when_no_limits_configured() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest),
                AiMessage.from("Done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(result.content()).isEqualTo("Done");
        assertThat(searchInvocations).hasSize(3);
    }

    @Test
    void should_expose_tool_invocation_counts() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();
        ToolExecutionRequest calcRequest = ToolExecutionRequest.builder()
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();

        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest),
                AiMessage.from(calcRequest),
                AiMessage.from(searchRequest),
                AiMessage.from("Done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .build();

        Result<String> result = assistant.chat("do things");

        assertThat(result.content()).isEqualTo("Done");
        assertThat(result.toolExecutionCounts()).containsEntry("search", 2);
        assertThat(result.toolExecutionCounts()).containsEntry("calculate", 1);
    }

    @Test
    void should_make_final_no_tools_call_when_all_tools_exhausted() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        AtomicInteger modelCallCount = new AtomicInteger();

        ChatModel model = new ChatModel() {
            final Queue<AiMessage> queue = new ConcurrentLinkedQueue<>(
                    asList(AiMessage.from(searchRequest), AiMessage.from("All tools exhausted, here is my answer")));

            @Override
            public ChatResponse doChat(ChatRequest request) {
                modelCallCount.incrementAndGet();
                AiMessage next = queue.poll();
                return ChatResponse.builder()
                        .aiMessage(next != null ? next : AiMessage.from("Fallback"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().defaultLimit(1).build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(searchInvocations).hasSize(1);
        // Model called twice: once with tools (search executes, hits limit),
        // then once more with no tools so the model can produce a text answer.
        assertThat(modelCallCount.get()).isEqualTo(2);
        assertThat(result.content()).isEqualTo("All tools exhausted, here is my answer");
    }

    @Test
    void should_remove_exhausted_tool_from_subsequent_llm_request() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();
        ToolExecutionRequest calcRequest = ToolExecutionRequest.builder()
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();

        List<ChatRequest> capturedRequests = new ArrayList<>();

        // Round 1: search executed (count=1, at limit)
        // After round 1: search removed, calc remains → loop continues
        // Round 2: model calls calc (within budget) → executed
        // After round 2: calc at limit, all tools exhausted → loop ends
        ChatModel model = new ChatModel() {
            final Queue<AiMessage> queue =
                    new ConcurrentLinkedQueue<>(asList(AiMessage.from(searchRequest), AiMessage.from(calcRequest)));

            @Override
            public ChatResponse doChat(ChatRequest request) {
                capturedRequests.add(request);
                AiMessage next = queue.poll();
                return ChatResponse.builder()
                        .aiMessage(next != null ? next : AiMessage.from("Fallback"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .build();
            }
        };

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(
                        ToolExecutionLimits.builder().defaultLimit(1).build())
                .build();

        assistant.chat("do things");

        // The second request to the model should NOT contain search in its tool specs
        assertThat(capturedRequests).hasSizeGreaterThanOrEqualTo(2);
        ChatRequest secondRequest = capturedRequests.get(1);
        List<String> toolNames = secondRequest.parameters().toolSpecifications().stream()
                .map(spec -> spec.name())
                .toList();
        assertThat(toolNames).doesNotContain("search");
        assertThat(toolNames).contains("calculate");
    }

    @Test
    void should_produce_text_answer_on_end_behavior_with_multiple_tools() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Round 1: search executed (count=1, at limit)
        // Round 2: model returns search again → END fires
        // Round 3 (final): LLM called with no tools, produces text answer
        ChatModel model = sequentialModel(
                AiMessage.from(searchRequest),
                AiMessage.from(searchRequest),
                AiMessage.from("Here is the final answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("search", 1, ToolLimitExceededBehavior.END)
                        .build())
                .build();

        Result<String> result = assistant.chat("search");

        assertThat(searchInvocations).hasSize(1);
        assertThat(result.content()).isEqualTo("Here is the final answer");
        // The result should have tool executions from the first round
        assertThat(result.toolExecutions()).hasSize(1);
    }

    @Test
    void should_handle_different_limits_and_behaviors_per_tool() {
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();
        ToolExecutionRequest calcRequest1 = ToolExecutionRequest.builder()
                .id("c1")
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();
        ToolExecutionRequest calcRequest2 = ToolExecutionRequest.builder()
                .id("c2")
                .name("calculate")
                .arguments("{\"arg0\":\"2+2\"}")
                .build();

        // search: limit=2 with CONTINUE (default)
        // calculate: limit=1 with ERROR
        // Round 1: search executes (count=1)
        // Round 2: model returns [calc, calc] in parallel → during budget check,
        //          calc1 is within budget (projected=0 < 1), calc2 is over budget
        //          (projected=1 >= 1) → ERROR throws before any tool in this batch executes
        ChatModel model = sequentialModel(AiMessage.from(searchRequest), AiMessage.from(calcRequest1, calcRequest2));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("search", 2)
                        .maxExecutions("calculate", 1, ToolLimitExceededBehavior.ERROR)
                        .build())
                .build();

        assertThatThrownBy(() -> assistant.chat("do things"))
                .isInstanceOf(ToolExecutionLimitExceededException.class)
                .hasMessageContaining("calculate");

        assertThat(searchInvocations).hasSize(1);
        // calc1 was within budget but ERROR fires before execute() for the whole batch
        assertThat(calcInvocations).isEmpty();
    }

    @Test
    void should_not_execute_sibling_tools_when_end_fires_in_parallel_response() {
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

        // Round 1: calculate called once (count=1, at limit with END)
        // Round 2: model returns [calculate, search] in parallel
        //   calculate is over-budget → END fires
        //   search should NOT execute (END halts the loop)
        ChatModel model = sequentialModel(
                AiMessage.from(calcRequest), AiMessage.from(calcRequest, searchRequest), AiMessage.from("END answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("calculate", 1, ToolLimitExceededBehavior.END)
                        .build())
                .build();

        Result<String> result = assistant.chat("do things");

        assertThat(calcInvocations).hasSize(1);
        // search should NOT have been executed since END halted the loop
        assertThat(searchInvocations).isEmpty();
        assertThat(result.content()).isEqualTo("END answer");
    }

    @Test
    void should_not_execute_sibling_tools_when_error_fires_in_parallel_response() {
        ToolExecutionRequest calcRequest1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculate")
                .arguments("{\"arg0\":\"1+1\"}")
                .build();
        ToolExecutionRequest calcRequest2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculate")
                .arguments("{\"arg0\":\"2+2\"}")
                .build();
        ToolExecutionRequest searchRequest = ToolExecutionRequest.builder()
                .id("3")
                .name("search")
                .arguments("{\"arg0\":\"q\"}")
                .build();

        // Model returns [calc, calc, search] in one response
        // calc1: within budget (projected 0 < 1), calc2: over budget → ERROR
        // search should NOT execute
        ChatModel model = sequentialModel(AiMessage.from(calcRequest1, calcRequest2, searchRequest));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new SearchTool(), new CalculatorTool())
                .toolExecutionLimits(ToolExecutionLimits.builder()
                        .maxExecutions("calculate", 1, ToolLimitExceededBehavior.ERROR)
                        .build())
                .build();

        assertThatThrownBy(() -> assistant.chat("do things"))
                .isInstanceOf(ToolExecutionLimitExceededException.class)
                .hasMessageContaining("calculate");

        // Neither calc nor search should have executed — ERROR fires during budget check
        assertThat(calcInvocations).isEmpty();
        assertThat(searchInvocations).isEmpty();
    }

    @Test
    void should_reject_negative_default_limit() {
        assertThatThrownBy(() -> ToolExecutionLimits.builder().defaultLimit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_negative_per_tool_limit() {
        assertThatThrownBy(() -> ToolExecutionLimits.builder().maxExecutions("search", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
