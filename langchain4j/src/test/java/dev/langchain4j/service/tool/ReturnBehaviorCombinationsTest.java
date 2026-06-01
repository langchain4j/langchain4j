package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE;
import static dev.langchain4j.agent.tool.ReturnBehavior.IMMEDIATE_IF_LAST;
import static dev.langchain4j.agent.tool.ReturnBehavior.TO_LLM;
import static dev.langchain4j.service.tool.ReturnBehaviorCombinationsTest.Outcome.RETURN_IMMEDIATELY;
import static dev.langchain4j.service.tool.ReturnBehaviorCombinationsTest.Outcome.REPROCESS;
import static dev.langchain4j.service.tool.ReturnBehaviorCombinationsTest.ToolStep.err;
import static dev.langchain4j.service.tool.ReturnBehaviorCombinationsTest.ToolStep.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Parameterized verification of the immediate-return/reprocess decision for every interesting combination
 * of {@link ReturnBehavior}s in a single LLM response. Each step is either {@link ToolStep#ok}
 * (executes successfully) or {@link ToolStep#err} (throws). Any error in any tool is expected
 * to force REPROCESS regardless of the other steps' behaviors.
 * <p>
 * The test is the source of truth for the intended semantics — when a row fails, either the
 * implementation needs to change or the row needs to change.
 */
class ReturnBehaviorCombinationsTest {

    interface Assistant {
        Result<String> chat(String message);
    }

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class Tools {

        @Tool(returnBehavior = TO_LLM)
        public String to_llm_ok() {
            return "to-llm";
        }

        @Tool(returnBehavior = TO_LLM)
        public String to_llm_err() {
            throw new IllegalStateException("boom (to_llm)");
        }

        @Tool(returnBehavior = IMMEDIATE)
        public String immediate_ok() {
            return "immediate";
        }

        @Tool(returnBehavior = IMMEDIATE)
        public String immediate_err() {
            throw new IllegalStateException("boom (immediate)");
        }

        @Tool(returnBehavior = IMMEDIATE_IF_LAST)
        public String immediate_if_last_ok() {
            return "immediate-if-last";
        }

        @Tool(returnBehavior = IMMEDIATE_IF_LAST)
        public String immediate_if_last_err() {
            throw new IllegalStateException("boom (immediate_if_last)");
        }
    }

    enum Outcome {
        RETURN_IMMEDIATELY,
        REPROCESS
    }

    record ToolStep(ReturnBehavior behavior, boolean errors) {

        static ToolStep ok(ReturnBehavior behavior) {
            return new ToolStep(behavior, false);
        }

        static ToolStep err(ReturnBehavior behavior) {
            return new ToolStep(behavior, true);
        }

        @Override
        public String toString() {
            return errors ? behavior + "(err)" : behavior.name();
        }
    }

    static Stream<Arguments> combinations() {
        return Stream.of(
                // --- TO_LLM ---
                arguments(List.of(ok(TO_LLM)), REPROCESS),
                arguments(List.of(ok(TO_LLM), ok(TO_LLM)), REPROCESS),

                // --- IMMEDIATE / TO_LLM ---
                arguments(List.of(ok(IMMEDIATE)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(IMMEDIATE), ok(IMMEDIATE)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(TO_LLM), ok(IMMEDIATE)), REPROCESS),
                arguments(List.of(ok(IMMEDIATE), ok(TO_LLM)), REPROCESS),

                // --- IMMEDIATE_IF_LAST / TO_LLM ---
                arguments(List.of(ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(IMMEDIATE_IF_LAST), ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(TO_LLM), ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(IMMEDIATE_IF_LAST), ok(TO_LLM)), REPROCESS),

                // --- IMMEDIATE / IMMEDIATE_IF_LAST ---
                arguments(List.of(ok(IMMEDIATE), ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(IMMEDIATE_IF_LAST), ok(IMMEDIATE)), RETURN_IMMEDIATELY),

                // --- IMMEDIATE / IMMEDIATE_IF_LAST / TO_LLM ---
                arguments(List.of(ok(TO_LLM), ok(IMMEDIATE), ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(TO_LLM), ok(IMMEDIATE_IF_LAST), ok(IMMEDIATE)), REPROCESS),
                arguments(List.of(ok(IMMEDIATE), ok(TO_LLM), ok(IMMEDIATE_IF_LAST)), RETURN_IMMEDIATELY),
                arguments(List.of(ok(IMMEDIATE), ok(IMMEDIATE_IF_LAST), ok(TO_LLM)), REPROCESS),
                arguments(List.of(ok(IMMEDIATE_IF_LAST), ok(TO_LLM), ok(IMMEDIATE)), REPROCESS),
                arguments(List.of(ok(IMMEDIATE_IF_LAST), ok(IMMEDIATE), ok(TO_LLM)), REPROCESS),

                // --- ERRORS: any error in any tool => REPROCESS ---

                // single tool errors (the only call in the response errors)
                arguments(List.of(err(TO_LLM)), REPROCESS),
                arguments(List.of(err(IMMEDIATE)), REPROCESS),                // would return immediately without error
                arguments(List.of(err(IMMEDIATE_IF_LAST)), REPROCESS),        // would return immediately without error

                // two tools, one errors — covers both first-errors and last-errors positions
                arguments(List.of(err(IMMEDIATE), ok(IMMEDIATE)), REPROCESS), // would return immediately without error
                arguments(List.of(ok(IMMEDIATE), err(IMMEDIATE)), REPROCESS), // would return immediately without error
                arguments(List.of(err(TO_LLM), ok(IMMEDIATE_IF_LAST)), REPROCESS),  // would return immediately without error
                arguments(List.of(ok(TO_LLM), err(IMMEDIATE_IF_LAST)), REPROCESS),  // would return immediately without error (last itself errors)
                arguments(List.of(err(IMMEDIATE_IF_LAST), ok(TO_LLM)), REPROCESS),  // already REPROCESS without error
                arguments(List.of(err(IMMEDIATE), ok(IMMEDIATE_IF_LAST)), REPROCESS),  // would return immediately without error
                arguments(List.of(ok(IMMEDIATE), err(IMMEDIATE_IF_LAST)), REPROCESS),  // would return immediately without error (last itself errors)

                // three tools, error in any position — would-return-immediately mixes
                arguments(List.of(err(TO_LLM), ok(IMMEDIATE), ok(IMMEDIATE_IF_LAST)), REPROCESS),
                arguments(List.of(ok(TO_LLM), err(IMMEDIATE), ok(IMMEDIATE_IF_LAST)), REPROCESS),
                arguments(List.of(ok(TO_LLM), ok(IMMEDIATE), err(IMMEDIATE_IF_LAST)), REPROCESS));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("combinations")
    void should_produce_expected_outcome(List<ToolStep> steps, Outcome expectedOutcome) {

        List<ToolExecutionRequest> toolRequests = toolRequestsFor(steps);

        // First LLM response: the tool calls under test.
        // Second LLM response: a final text answer, consumed only when the loop reprocesses.
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolRequests.toArray(new ToolExecutionRequest[0])),
                AiMessage.from("final answer"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new Tools())
                .build();

        Result<String> result = assistant.chat("go");

        if (expectedOutcome == RETURN_IMMEDIATELY) {
            assertThat(model.requests())
                    .as("immediate return means a single LLM call (no reprocessing round trip)")
                    .hasSize(1);
            assertThat(result.content())
                    .as("immediate return means no LLM-generated text content")
                    .isNull();
        } else {
            assertThat(model.requests())
                    .as("REPROCESS means a second LLM call to consume the tool results")
                    .hasSize(2);
            assertThat(result.content())
                    .as("REPROCESS means the second LLM response wins")
                    .isEqualTo("final answer");
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("combinations")
    void should_produce_expected_outcome__streaming(List<ToolStep> steps, Outcome expectedOutcome) throws Exception {

        List<ToolExecutionRequest> toolRequests = toolRequestsFor(steps);

        // First LLM response: the tool calls under test.
        // Second LLM response: a final text answer, consumed only when the loop reprocesses.
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolRequests.toArray(new ToolExecutionRequest[0])),
                AiMessage.from("final answer"));

        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .tools(new Tools())
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat("go")
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse finalResponse = future.get(10, TimeUnit.SECONDS);

        if (expectedOutcome == RETURN_IMMEDIATELY) {
            assertThat(model.requests())
                    .as("immediate return means a single LLM call (no reprocessing round trip)")
                    .hasSize(1);
            assertThat(finalResponse.aiMessage().hasToolExecutionRequests())
                    .as("immediate return means the final response carries the tool calls, not LLM text")
                    .isTrue();
        } else {
            assertThat(model.requests())
                    .as("REPROCESS means a second LLM call to consume the tool results")
                    .hasSize(2);
            assertThat(finalResponse.aiMessage().text())
                    .as("REPROCESS means the second LLM response wins")
                    .isEqualTo("final answer");
        }
    }

    private static List<ToolExecutionRequest> toolRequestsFor(List<ToolStep> steps) {
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            toolRequests.add(ToolExecutionRequest.builder()
                    .id("call-" + i)
                    .name(toolNameFor(steps.get(i)))
                    .arguments("{}")
                    .build());
        }
        return toolRequests;
    }

    private static String toolNameFor(ToolStep step) {
        String prefix = switch (step.behavior()) {
            case TO_LLM -> "to_llm";
            case IMMEDIATE -> "immediate";
            case IMMEDIATE_IF_LAST -> "immediate_if_last";
        };
        return prefix + (step.errors() ? "_err" : "_ok");
    }
}
