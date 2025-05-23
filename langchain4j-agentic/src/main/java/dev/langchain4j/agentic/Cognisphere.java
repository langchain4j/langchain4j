package dev.langchain4j.agentic;

import dev.langchain4j.agentic.internal.AgentCall;
import dev.langchain4j.agentic.internal.AgentSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Cognisphere {

    private final String id;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Map<String, List<AgentCall>> agentsInvocations = new ConcurrentHashMap<>();

    public Cognisphere() {
        this(UUID.randomUUID().toString());
    }

    public Cognisphere(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void writeState(String key, Object value) {
        state.put(key, value);
    }

    public void writeStates(Map<String, Object> newState) {
        state.putAll(newState);
    }

    public Object readState(String key) {
        return state.get(key);
    }

    public <T> T readState(String key, T defaultValue) {
        return (T) state.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void registerAgentCall(AgentSpecification agentSpecification, Object[] input, Object response) {
        agentsInvocations.computeIfAbsent(agentSpecification.name(), agent -> new ArrayList<>())
                .add(new AgentCall(agentSpecification.name(), input, response));
    }

    public List<AgentCall> getAgentInvocations(String agentName) {
        return agentsInvocations.getOrDefault(agentName, List.of());
    }

    @Override
    public String toString() {
        return "Cognisphere{" +
                "id='" + id + '\'' +
                ", state=" + state +
                '}';
    }
}
