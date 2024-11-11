package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Classifies a given text based on a set of labels.
 * It can return zero, one, or multiple labels for each classification.
 *
 * @param <L> The type of the label  (e.g., String, Enum, etc.)
 */
public interface TextClassifier<L> {

    /**
     * Classify the given text.
     *
     * @param text Text to classify.
     * @return A list of classification categories.
     */
    default List<L> classify(String text) {
        return classifyWithScore(text).scoredLabels().stream()
            .map(ScoredLabel::label)
            .collect(toList());
    }

    /**
     * Classify the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of classification categories.
     */
    default List<L> classify(TextSegment textSegment) {
        return classify(textSegment.text());
    }

    /**
     * Classify the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of classification categories.
     */
    default List<L> classify(Document document) {
        return classify(document.text());
    }

    /**
     * Classify the given text with score.
     *
     * @param text Text to classify.
     * @return A list of classification categories and detailed results
     */
    ClassificationResult<L> classifyWithScore(String text);

    /**
     * Classify the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of classification categories and detailed results
     */
    default ClassificationResult<L> classifyWithScore(TextSegment textSegment) {
        return classifyWithScore(textSegment.text());
    }

    /**
     * Classify the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of classification categories and detailed results
     */
    default ClassificationResult<L> classifyWithScore(Document document) {
        return classifyWithScore(document.text());
    }
}
