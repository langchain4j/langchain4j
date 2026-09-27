package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeSafeUsage {

    public Integer inputTokens;
    public Integer outputTokens;
}
