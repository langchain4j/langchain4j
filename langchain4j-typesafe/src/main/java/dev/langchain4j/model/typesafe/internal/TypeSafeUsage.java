package dev.langchain4j.model.typesafe.internal;

import dev.langchain4j.Internal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
@Internal
public class TypeSafeUsage {

    public Integer inputTokens;
    public Integer outputTokens;
}
