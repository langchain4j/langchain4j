package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentUtil;
import java.lang.reflect.Type;

public record AgentArgument(Type type, String name, Object defaultValue, boolean isOptional) {

    public AgentArgument(Type type, String name) {
        this(type, name, null);
    }

    public AgentArgument(Type type, String name, Object defaultValue) {
        this(type, name, defaultValue, false);
    }

    public Class<?> rawType() {
        return AgentUtil.rawType(type);
    }
}
