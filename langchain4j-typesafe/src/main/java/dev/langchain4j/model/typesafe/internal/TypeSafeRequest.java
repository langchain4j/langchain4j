package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;
import java.util.Map;

@Internal
public class TypeSafeRequest {

    public String model;
    public Object state;
    public Map<String, TypeSafeQuestion> questions;

    /**
     * The images, by the reference that stands for each of them in the {@link #state}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Map<String, String>> assets;
}
