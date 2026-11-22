package dev.langchain4j.agentic.patterns.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that a debate resumes at the point where it was suspended, instead of starting a new debate.
 */
class DebatePlannerResumeTest {

    static class JsonInMemoryStore implements AgenticScopeStore {

        private final Map<AgenticScopeKey, String> scopes = new ConcurrentHashMap<>();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            scopes.put(key, AgenticScopeSerializer.toJson(agenticScope));
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            return Optional.ofNullable(scopes.get(key)).map(AgenticScopeSerializer::fromJson);
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            return scopes.remove(key) != null;
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            return scopes.keySet();
        }
    }

    public interface ReviewPanel extends AgenticScopeAccess {

        @Agent
        String review(@MemoryId String sessionId, @V("topic") String topic);
    }

    private final AtomicInteger debaterInvocations = new AtomicInteger();
    private final AtomicInteger verdictWrites = new AtomicInteger();

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void debate_resumes_at_a_judge_that_suspended() {
        AgenticScopePersister.setStore(new JsonInMemoryStore());

        // the judge asks a human for the approval and then turns it into the final verdict
        UntypedAgent judge = AgenticServices.sequenceBuilder()
                .subAgents(
                        AgenticServices.humanInTheLoopBuilder()
                                .outputKey("approval")
                                .responseProvider(scope -> new SuspendedResponse<>("approval"))
                                .build(),
                        AgenticServices.nonAiAgentBuilder(scope -> {
                                    verdictWrites.incrementAndGet();
                                    return "final:" + scope.readState("approval");
                                })
                                .name("verdictWriter")
                                .outputKey("verdict")
                                .build())
                .outputKey("verdict")
                .build();

        ReviewPanel panel = AgenticServices.plannerBuilder(ReviewPanel.class)
                .subAgents(debater("optimist"), debater("pessimist"), judge)
                .outputKey("verdict")
                .planner(DebatePlanner::new)
                .build();

        assertThatThrownBy(() -> panel.review("s1", "mars")).isInstanceOf(AgenticSystemSuspendedException.class);

        panel.getAgenticScope("s1").completePendingResponse("APPROVED");

        assertThat(panel.review("s1", "mars")).isEqualTo("final:APPROVED");
        assertThat(debaterInvocations).hasValue(2);
        assertThat(verdictWrites).hasValue(1);
    }

    private AgenticScopeFunction<String> debater(String name) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    debaterInvocations.incrementAndGet();
                    return "it works";
                })
                .name(name)
                .outputKey(name)
                .build();
    }
}
