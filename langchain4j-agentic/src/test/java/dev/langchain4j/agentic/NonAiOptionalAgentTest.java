package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code optional} attribute of {@code @Agent} is honored for non-AI agents: when one of its
 * arguments is missing from the agentic scope, the agent is skipped instead of failing the whole
 * agentic system.
 */
class NonAiOptionalAgentTest {

    static final AtomicInteger FIX_INVOCATIONS = new AtomicInteger();

    public static class Analyzer {

        @Agent(description = "Analyzes the request", outputKey = "analysis")
        public static String analyze(@V("request") String request) {
            return "analysis of " + request;
        }
    }

    public static class FixDelegator {

        @Agent(description = "Delegates the fix to a remote service", outputKey = "fix", optional = true)
        public static String applyFix(@V("repoUrl") String repoUrl) {
            FIX_INVOCATIONS.incrementAndGet();
            return "fixed " + repoUrl;
        }
    }

    public static class MandatoryFixDelegator {

        @Agent(description = "Delegates the fix to a remote service", outputKey = "fix")
        public static String applyFix(@V("repoUrl") String repoUrl) {
            FIX_INVOCATIONS.incrementAndGet();
            return "fixed " + repoUrl;
        }
    }

    public interface Pipeline {

        @SequenceAgent(
                outputKey = "analysis",
                subAgents = {Analyzer.class, FixDelegator.class})
        ResultWithAgenticScope<String> process(@V("request") String request);
    }

    @BeforeEach
    void resetInvocations() {
        FIX_INVOCATIONS.set(0);
    }

    @Test
    void optional_non_ai_agent_is_skipped_when_its_argument_is_missing() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(Analyzer.class, FixDelegator.class)
                .outputKey("analysis")
                .build();

        assertThat(workflow.invoke(Map.of("request", "the request"))).isEqualTo("analysis of the request");
        assertThat(FIX_INVOCATIONS).hasValue(0);
    }

    @Test
    void optional_non_ai_agent_runs_when_its_argument_is_present() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(Analyzer.class, FixDelegator.class)
                .outputKey("fix")
                .build();

        assertThat(workflow.invoke(Map.of("request", "the request", "repoUrl", "http://repo")))
                .isEqualTo("fixed http://repo");
        assertThat(FIX_INVOCATIONS).hasValue(1);
    }

    @Test
    void non_optional_non_ai_agent_fails_when_its_argument_is_missing() {
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(Analyzer.class, MandatoryFixDelegator.class)
                .outputKey("analysis")
                .build();

        assertThat(assertThrows(
                        MissingArgumentException.class, () -> workflow.invoke(Map.of("request", "the request"))))
                .hasMessageContaining("repoUrl");
    }

    @Test
    void declarative_optional_non_ai_agent_is_skipped_in_a_sequence() {
        Pipeline pipeline = AgenticServices.createAgenticSystem(Pipeline.class);

        ResultWithAgenticScope<String> result = pipeline.process("the request");

        assertThat(result.result()).isEqualTo("analysis of the request");
        assertThat(result.agenticScope().readState("fix")).isNull();
        assertThat(result.agenticScope().readState("analysis")).isEqualTo("analysis of the request");
        assertThat(FIX_INVOCATIONS).hasValue(0);
    }
}
