package dev.langchain4j.model.cohere;

import lombok.Builder;
import lombok.NonNull;

@Builder
public class ParameterDefinition {

    String description;

    @NonNull
    String type;

    boolean required;
}
