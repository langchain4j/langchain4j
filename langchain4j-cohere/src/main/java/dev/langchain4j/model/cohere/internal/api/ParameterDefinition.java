package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;
import lombok.NonNull;

@Builder
public class ParameterDefinition {

    String description;

    @NonNull
    String type;

    Boolean required;
}
