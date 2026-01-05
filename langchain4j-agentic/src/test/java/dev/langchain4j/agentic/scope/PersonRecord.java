package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PersonRecord(
        String name,
        int age,
        @JsonProperty("is_present")
        Boolean isPresent
) {}