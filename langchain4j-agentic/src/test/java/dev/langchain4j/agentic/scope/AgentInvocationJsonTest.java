package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentInvocationJsonTest {

    @Test
    void persisted_invocation_has_no_skip_reason_and_is_read_back_as_not_skipped() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        scope.registerAgentInvocation(
                new AgentInvocation(String.class, "writer", "writer$0", Map.of("topic", "dragons"), "story"), null);

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        // a scope persisted before invocations had a 'skipReason' property looks the same
        assertThat(json).contains("\"agentName\":\"writer\"").doesNotContain("skip");
        assertThat(deserialized.agentInvocations()).singleElement().satisfies(invocation -> {
            assertThat(invocation.agentName()).isEqualTo("writer");
            assertThat(invocation.output()).isEqualTo("story");
            assertThat(invocation.skipped()).isFalse();
        });
    }
}
