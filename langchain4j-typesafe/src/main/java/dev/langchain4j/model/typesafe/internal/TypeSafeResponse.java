package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.Internal;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Internal
public class TypeSafeResponse {

    public String model;
    public Map<String, TypeSafeAnswer> answers;
    public TypeSafeUsage usage;

    @JsonIgnore
    public SuccessfulHttpResponse rawHttpResponse;
}
