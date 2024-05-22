package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;
import lombok.NonNull;

import java.util.Map;


@Builder
public class Tool {

    @NonNull
    String name;

    @NonNull
    String description;

    Map<String, ParameterDefinition> parameterDefinitions;

}
