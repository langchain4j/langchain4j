package dev.langchain4j.model.typesafe.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TypeSafeAnswer {

    public String type;
    public Double noul;
    public String choice;
    public Double score;
    public Map<String, Double> probabilities;
    public Double confidence;
}
