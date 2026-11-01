package dev.langchain4j.model.typesafe.internal;

import dev.langchain4j.Internal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Internal
public class TypeSafeResponse {

    public String model;
    public Map<String, TypeSafeAnswer> answers;
    public TypeSafeUsage usage;
}
