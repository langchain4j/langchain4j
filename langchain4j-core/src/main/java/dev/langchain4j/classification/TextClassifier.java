package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * Classifies given text according to specified enum.
 *
 * @param <E> Enum that is the result of classification.
 */
public interface TextClassifier<E extends Enum<E>> {

    /**
     * Classify the given text.
     *
     * @param text Text to classify.
     * @return A list of classification categories.
     */
    List<E> classify(String text);

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
