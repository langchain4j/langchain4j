package dev.langchain4j.agentic.scope;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.agentic.internal.DelayedResponse;

import java.util.Map;

@JsonInclude(NON_NULL)
public record AgentInvocation(
        @JsonProperty("agentType") Class<?> agentType,
        @JsonProperty("agentName") String agentName,
        @JsonProperty("agentId") String agentId,
        @JsonProperty("input") Map<String, Object> input,
        @JsonProperty("output") Object output) {

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
                '}';
    }
}
