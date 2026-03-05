package dev.langchain4j.agentic.internal;

import java.lang.reflect.Parameter;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.internal.AgentUtil.keyName;

public class AgenticParameterNameResolver implements ParameterNameResolver {

    @Override
    public boolean hasVariableName(final Parameter parameter) {
        return getVariableName(parameter) != null;
    }

    @Override
    public String getVariableName(Parameter parameter) {
        if (parameter.isNamePresent()) {
            return parameter.getName();
        }

        V annotation = parameter.getAnnotation(V.class);
        if (annotation != null) {
            return annotation.value();
        }

        K k = parameter.getAnnotation(K.class);
        if (k != null) {
            return keyName(k.value());
        }

        if (parameter.getAnnotation(MemoryId.class) != null) {
            return "@MemoryId";
        }
        if (parameter.getAnnotation(UserMessage.class) != null) {
            return "@UserMessage";
        }

        return null;
    }
}
