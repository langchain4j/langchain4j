package dev.langchain4j.agentic.workflow.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.Agents;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Verifies that LoopPlanner respects maxIterations exactly,
 * without running an extra round (off-by-one).
 */
class LoopPlannerTest {

    @Test
    void singleAgent_maxIterations1_runsExactly1Round() {
        AtomicInteger callCount = new AtomicInteger(0);

        ChatModel countingModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                callCount.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("0.5")).build();
            }
        };

        Agents.StyleScorer scorer = AgenticServices.agentBuilder(Agents.StyleScorer.class)
                .chatModel(countingModel)
                .outputKey("score")
                .build();

        UntypedAgent loop = AgenticServices.loopBuilder()
                .name("testLoop")
                .subAgents(scorer)
                .maxIterations(1)
                .exitCondition("score >= 0.8", scope -> scope.readState("score", 0.0) >= 0.8)
                .build();

        loop.invokeWithAgenticScope(Map.of("story", "A test story", "style", "comedy"));

        assertThat(callCount.get())
                .as("maxIterations=1 with single agent should run exactly 1 round")
                .isEqualTo(1);
    }

    @Test
    void twoAgents_maxIterations3_runsExactly3RoundsEach() {
        AtomicInteger scorerCalls = new AtomicInteger(0);
        AtomicInteger editorCalls = new AtomicInteger(0);

        ChatModel scorerModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                scorerCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("0.5")).build();
            }
        };

        ChatModel editorModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                editorCalls.incrementAndGet();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("edited story"))
                        .build();
            }
        };

        Agents.StyleScorer scorer = AgenticServices.agentBuilder(Agents.StyleScorer.class)
                .chatModel(scorerModel)
                .outputKey("score")
                .build();

        Agents.StyleEditor editor = AgenticServices.agentBuilder(Agents.StyleEditor.class)
                .chatModel(editorModel)
                .outputKey("story")
                .build();

        UntypedAgent loop = AgenticServices.loopBuilder()
                .name("testLoop")
                .subAgents(scorer, editor)
                .maxIterations(3)
                .exitCondition("score >= 0.8", scope -> scope.readState("score", 0.0) >= 0.8)
                .build();

        loop.invokeWithAgenticScope(Map.of("story", "A test story", "style", "comedy"));

        assertThat(scorerCalls.get())
                .as("scorer should be invoked exactly 3 times (3 rounds)")
                .isEqualTo(3);
        assertThat(editorCalls.get())
                .as("editor should be invoked exactly 3 times (3 rounds)")
                .isEqualTo(3);
    }
}
