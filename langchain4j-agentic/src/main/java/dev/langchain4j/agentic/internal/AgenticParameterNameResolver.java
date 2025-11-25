package dev.langchain4j.agentic.internal;

import java.lang.reflect.Parameter;
import java.util.Optional;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.V;

import static dev.langchain4j.agentic.internal.AgentUtil.stateName;

public class AgenticParameterNameResolver implements ParameterNameResolver {

    @Override
    public String getVariableName(Parameter parameter) {
        V annotation = parameter.getAnnotation(V.class);
        if (annotation != null) {
            return annotation.value();
        }

        K k = parameter.getAnnotation(K.class);
        if (k != null) {
            return stateName(k.value());
        }

        return parameter.isNamePresent() ? parameter.getName() : null;
    }
}
