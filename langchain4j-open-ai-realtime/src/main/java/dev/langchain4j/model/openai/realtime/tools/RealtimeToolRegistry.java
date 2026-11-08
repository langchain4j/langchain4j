package dev.langchain4j.model.openai.realtime.tools;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry of tools available to the Realtime gateway, keyed by tool name.
 */
public final class RealtimeToolRegistry {

    private final Map<String, ToolSpecification> specsByName;
    private final Map<String, ToolExecutor> executorsByName;

    private RealtimeToolRegistry(
            Map<String, ToolSpecification> specsByName, Map<String, ToolExecutor> executorsByName) {
        this.specsByName = specsByName;
        this.executorsByName = executorsByName;
    }

    public static RealtimeToolRegistry from(Map<ToolSpecification, ToolExecutor> tools) {
        Objects.requireNonNull(tools, "tools");
        Map<String, ToolSpecification> specsByName = new LinkedHashMap<>();
        Map<String, ToolExecutor> executorsByName = new LinkedHashMap<>();
        for (Map.Entry<ToolSpecification, ToolExecutor> entry : tools.entrySet()) {
            ToolSpecification spec = Objects.requireNonNull(entry.getKey(), "toolSpecification");
            ToolExecutor executor = Objects.requireNonNull(entry.getValue(), "toolExecutor");
            String name = Objects.requireNonNull(spec.name(), "toolSpecification.name");
            if (specsByName.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate tool name: " + name);
            }
            specsByName.put(name, spec);
            executorsByName.put(name, executor);
        }
        return new RealtimeToolRegistry(specsByName, executorsByName);
    }

    public ToolExecutor findExecutor(String name) {
        return executorsByName.get(name);
    }

    public ToolSpecification findSpecification(String name) {
        return specsByName.get(name);
    }

    public List<ToolSpecification> allSpecifications() {
        return unmodifiableList(new ArrayList<>(specsByName.values()));
    }

    public Set<String> names() {
        return unmodifiableSet(specsByName.keySet());
    }
}
