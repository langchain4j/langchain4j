package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.internal.AgentUtil.keyName;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.lang.reflect.Parameter;

public class AgenticParameterNameResolver implements ParameterNameResolver {

    @Override
    public boolean hasVariableName(final Parameter parameter) {
        return getVariableName(parameter) != null;
    }

    @Override
    public String getVariableName(Parameter parameter) {
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

        if (parameter.isNamePresent()) {
            return parameter.getName();
        }

        return null;
    }
}
