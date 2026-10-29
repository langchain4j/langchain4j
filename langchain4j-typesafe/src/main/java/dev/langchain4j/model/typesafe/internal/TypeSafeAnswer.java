package dev.langchain4j.model.typesafe.internal;

import dev.langchain4j.Internal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Internal
public class TypeSafeAnswer {

    public String type;
    public Double noul;
    public String choice;
    public Double score;
    public Map<String, Double> probabilities;
    public Double confidence;
}
