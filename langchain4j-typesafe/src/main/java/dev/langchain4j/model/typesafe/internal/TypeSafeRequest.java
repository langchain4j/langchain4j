package dev.langchain4j.model.typesafe.internal;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.Map;

@Internal
public class TypeSafeRequest {

    @JsonInclude(NON_NULL)
    public String model;

    @JsonInclude(NON_NULL)
    public Object state;

    @JsonInclude(NON_NULL)
    public Map<String, TypeSafeQuestion> questions;

    @JsonIgnore
    public Map<String, Object> customParameters;

    @JsonAnyGetter
    Map<String, Object> customParameters() {
        return customParameters;
    }
}
