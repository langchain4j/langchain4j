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
     * Classifies the given text.
     *
     * @param text Text to classify.
     * @return A list of labels. Can contain zero, one, or multiple labels.
     */
    default List<L> classify(String text) {
        return classifyWithScores(text).scoredLabels().stream()
                .map(ScoredLabel::label)
                .collect(toList());
    }

    /**
     * Classifies the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of labels. Can contain zero, one, or multiple labels.
     */
    default List<L> classify(TextSegment textSegment) {
        return classify(textSegment.text());
    }

    /**
     * Classifies the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of labels. Can contain zero, one, or multiple labels.
     */
    default List<L> classify(Document document) {
        return classify(document.text());
    }

    /**
     * Classifies the given text and returns labels with scores.
     *
     * @param text Text to classify.
     * @return a result object containing a list of labels with corresponding scores.
     * Can contain zero, one, or multiple labels.
     */
    ClassificationResult<L> classifyWithScores(String text);

    /**
     * Classifies the given {@link TextSegment} and returns labels with scores.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return a result object containing a list of labels with corresponding scores.
     * Can contain zero, one, or multiple labels.
     */
    default ClassificationResult<L> classifyWithScores(TextSegment textSegment) {
        return classifyWithScores(textSegment.text());
    }

    /**
     * Classifies the given {@link Document} and returns labels with scores.
     *
     * @param document {@link Document} to classify.
     * @return a result object containing a list of labels with corresponding scores.
     * Can contain zero, one, or multiple labels.
     */
    default ClassificationResult<L> classifyWithScores(Document document) {
        return classifyWithScores(document.text());
    }
}
