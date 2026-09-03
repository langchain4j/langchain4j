package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The safety assessment of a Gemini candidate or of the prompt, as reported by the Gemini API for each
 * harm category.
 *
 * @param category    the harm category being assessed
 * @param probability the probability of the content belonging to the category (e.g. "NEGLIGIBLE", "LOW",
 *                    "MEDIUM", "HIGH")
 * @param severity    the severity of the content within the category (e.g. "NEGLIGIBLE", "LOW", "MEDIUM",
 *                    "HIGH"), when provided by the API
 * @param blocked     whether the content was blocked because of this category
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiSafetyRating(
        @JsonProperty("category") GeminiHarmCategory category,
        @JsonProperty("probability") String probability,
        @JsonProperty("severity") String severity,
        @JsonProperty("blocked") Boolean blocked) {}
