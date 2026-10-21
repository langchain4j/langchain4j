package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.Internal;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@Internal
public class TypeSafeAnswer {

    public String type;
    public Double noul;
    public String choice;
    public Double score;

    /**
     * The edges of a {@code box} answer: {@code top}, {@code bottom}, {@code left} and {@code right}.
     */
    public Map<String, Double> box;

    public Map<String, Double> probabilities;
    public Double confidence;
}
