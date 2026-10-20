package dev.langchain4j.agentic.scope;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agentic.internal.DelayedResponse;

import java.util.Map;

/**
 * The invocation of an agent, as notified to the planner of its parent.
 *
 * @param skipReason why the agent was not invoked, because its activation condition was false or because it is
 *        optional and one of its arguments was missing, or {@code null} when it was invoked. The output of a skipped
 *        agent is the current value of its output key. Skipped invocations are not registered in the
 *        {@link AgenticScope}.
 */
@JsonInclude(NON_NULL)
public record AgentInvocation(
        @JsonProperty("agentType") Class<?> agentType,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("agentId") String agentId,
        @JsonProperty("input") Map<String, Object> input,
        @JsonProperty("output") Object output,
        @JsonProperty("skipReason") String skipReason) {

    public AgentInvocation(
            Class<?> agentType, String agentName, String agentId, Map<String, Object> input, Object output) {
        this(agentType, agentName, agentId, input, output, null);
    }

    /**
     * Returns {@code true} if the agent was skipped instead of being invoked.
     */
    public boolean skipped() {
        return skipReason != null;
    }

    @Override
    public Object output() {
        return output instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : output;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName=" + agentName +
                ", input=" + input +
                ", output=" + output +
                (skipped() ? ", skipped: " + skipReason : "") +
                '}';
    }
}
