package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A safety assessment that Gemini reports for a single harm category.
 *
 * <p>Gemini evaluates both the prompt you send and the content it generates against a set of harm categories,
 * and returns one rating per category. Ratings are available from
 * {@link GoogleAiGeminiChatResponseMetadata#safetyRatings()} (for the generated content) and
 * {@link GoogleAiGeminiChatResponseMetadata#promptSafetyRatings()} (for the prompt):
 *
 * <pre>{@code
 * ChatResponse response = model.chat(ChatRequest.builder().messages(userMessage).build());
 *
 * var metadata = (GoogleAiGeminiChatResponseMetadata) response.metadata();
 * for (GeminiSafetyRating rating : metadata.safetyRatings()) {
 *     System.out.println(rating.category() + ": " + rating.probability());
 * }
 * }</pre>
 *
 * <p>{@code category} and {@code probability} are the raw values returned by the Gemini API rather than Java enums,
 * so that categories introduced by Google in the future are surfaced as-is instead of breaking response parsing.
 *
 * @param category    the harm category being assessed, for example {@code "HARM_CATEGORY_HARASSMENT"} or
 *                    {@code "HARM_CATEGORY_DANGEROUS_CONTENT"}
 * @param probability how likely the content is to belong to this category: {@code "NEGLIGIBLE"}, {@code "LOW"},
 *                    {@code "MEDIUM"} or {@code "HIGH"}
 * @param blocked     whether this category caused the content to be blocked; {@code null} when the API does not
 *                    report it
 * @see GoogleAiGeminiChatResponseMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiSafetyRating(
        @JsonProperty("category") String category,
        @JsonProperty("probability") String probability,
        @JsonProperty("blocked") Boolean blocked) {}
