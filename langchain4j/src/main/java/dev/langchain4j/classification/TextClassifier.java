package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Classifies given text according to specified enum.
 *
 * @param <E> Enum that is the result of classification.
 */
public interface TextClassifier<E extends Enum<E>> {

    /**
     * Classify the given text with score.
     *
     * @param text Text to classify.
     * @return A list of classification categories and scores.
     */
    List<LabelWithScore<E>> classifyWithScore(String text);

    /**
     * Classify the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of classification categories.
     */
    default List<LabelWithScore<E>> classifyWithScore(TextSegment textSegment) {
        return classifyWithScore(textSegment.text());
    }

    /**
     * Classify the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of classification categories.
     */
    default List<LabelWithScore<E>> classifyWithScore(Document document) {
        return classifyWithScore(document.text());
    }

    /**
     * Classify the given text.
     *
     * @param text Text to classify.
     * @return A list of classification categories.
     */
    default List<E> classify(String text) {
        return classifyWithScore(text).stream()
                .map(LabelWithScore::getLabel)
                .collect(Collectors.toList());
    }

    /**
     * Classify the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of classification categories.
     */
    default List<E> classify(TextSegment textSegment) {
        return classify(textSegment.text());
    }

    /**
     * Classify the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of classification categories.
     */
    default List<E> classify(Document document) {
        return classify(document.text());
    }
}
