package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Classifies given text according to specified enum.
 *
 * @param <L> Label type that is the result of classification.
 */
public interface TextClassifier<L> {

    /**
     * Classify the given text with score.
     *
     * @param text Text to classify.
     * @return A list of classification categories and detailed results
     */
    List<ClassifyResult<L>> classifyWithDetail(String text);

    /**
     * Classify the given {@link TextSegment}.
     *
     * @param textSegment {@link TextSegment} to classify.
     * @return A list of classification categories and detailed results
     */
    default List<ClassifyResult<L>> classifyWithDetail(TextSegment textSegment) {
        return classifyWithDetail(textSegment.text());
    }

    /**
     * Classify the given {@link Document}.
     *
     * @param document {@link Document} to classify.
     * @return A list of classification categories and detailed results
     */
    default List<ClassifyResult<L>> classifyWithDetail(Document document) {
        return classifyWithDetail(document.text());
    }

    /**
     * Classify the given text.
     *
     * @param text Text to classify.
     * @return A list of classification categories.
     */
    default List<L> classify(String text) {
        return classifyWithDetail(text).stream()
                .map(ClassifyResult::label)
                .collect(Collectors.toList());
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
}
