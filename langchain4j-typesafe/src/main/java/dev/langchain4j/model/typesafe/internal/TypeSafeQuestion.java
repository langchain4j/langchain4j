package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class TypeSafeQuestion {

    public String type;
    public String instructions;

    /**
     * The criteria, whose shape depends on the type: a map with the keys {@code true} and {@code false} for
     * {@code noul}, a map from option name to description (or {@code null}) for {@code choice}, and the list of
     * levels for {@code score}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object criteria;
}
