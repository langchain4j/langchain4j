package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.internal.AgentUtil;
import java.lang.reflect.Type;

public record AgentArgument(Type type, String name) {

    public Class<?> rawType() {
        return AgentUtil.rawType(type);
    }
}
