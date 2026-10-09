package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Supervisor + HumanInTheLoop via {@link SuspendedResponse}: the planner must not make another
 * LLM call while a suspended response is still pending (that turn would see
 * {@code <pending:?>} as {@code lastResponse} and pollute chat memory / burn a planning step).
 */
class SupervisorHitlSuspensionTest {

    public interface SuspendableSupervisor extends AgenticScopeAccess {
        ResultWithAgenticScope<String> invoke(@MemoryId String sessionId, @V("request") String request);
    }

    static class InMemoryAgenticScopeStore implements AgenticScopeStore {
        private final ConcurrentHashMap<AgenticScopeKey, DefaultAgenticScope> store = new ConcurrentHashMap<>();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            store.put(key, agenticScope);
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            return store.remove(key) != null;
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            return store.keySet();
        }
    }

    /** Counts planner {@code doChat} calls and returns scripted JSON replies in order. */
    static class CountingScriptedChatModel implements ChatModel {
        private final Iterator<String> responses;
        private final AtomicInteger calls = new AtomicInteger();

        CountingScriptedChatModel(String... responses) {
            this.responses = List.of(responses).iterator();
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            calls.incrementAndGet();
            if (!responses.hasNext()) {
                throw new AssertionError("Unexpected extra planner call #" + calls.get());
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responses.next()))
                    .build();
        }

        int calls() {
            return calls.get();
        }
    }

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void supervisor_does_not_plan_again_while_hitl_is_suspended() {
        AgenticScopePersister.setStore(new InMemoryAgenticScopeStore());

        CountingScriptedChatModel plannerModel = new CountingScriptedChatModel(
                """
                {"agentName": "askUser", "arguments": {"question": "What is your name?"}}
                """,
                """
                {"agentName": "query", "arguments": {}}
                """,
                """
                {"agentName": "done", "arguments": {"response": "Hello Alice"}}
                """);

        HumanInTheLoop askUser = AgenticServices.humanInTheLoopBuilder()
                .description("Ask the user for missing information")
                .outputKey("userAnswer")
                .responseProvider(scope -> new SuspendedResponse<>("user-name"))
                .build();

        var queryAgent = AgenticServices.nonAiAgentBuilder(scope -> {
                    String answer = scope.readState("userAnswer", "");
                    return "queried:" + answer;
                })
                .name("query")
                .description("Query agent that uses the user answer")
                .outputKey("queryResult")
                .build();

        SuspendableSupervisor supervisor = AgenticServices.supervisorBuilder(SuspendableSupervisor.class)
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .contextGenerationStrategy(SupervisorContextStrategy.CHAT_MEMORY)
                .subAgents(askUser, queryAgent)
                .build();

        // ResultWithAgenticScope returns with suspended=true instead of throwing.
        ResultWithAgenticScope<String> suspended = supervisor.invoke("s1", "Greet the user");
        assertThat(suspended.suspended()).isTrue();
        assertThat(suspended.agenticScope().pendingResponseIds()).containsExactly("user-name");

        // The critical assertion: only the turn that chose askUser may have hit the planner.
        // A second call here would mean we planned against a still-pending SuspendedResponse.
        assertThat(plannerModel.calls()).isEqualTo(1);

        ResultWithAgenticScope<String> result =
                suspended.completePendingResponse("user-name", "Alice");

        assertThat(result.suspended()).isFalse();
        assertThat(result.agenticScope().readState("userAnswer", "")).isEqualTo("Alice");
        assertThat(result.agenticScope().readState("queryResult", "")).isEqualTo("queried:Alice");
        assertThat(plannerModel.calls()).isEqualTo(3);
    }
}
