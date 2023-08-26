package dev.langchain4j.agent.tool.lambda;

import java.io.Serializable;

public interface ToolSerializedFunction extends Serializable {

    @SuppressWarnings("unchecked")
    default <T> T sneakyCast(Object value) {
        if (value == null) return null;
        return ((T) value);
    }
}
