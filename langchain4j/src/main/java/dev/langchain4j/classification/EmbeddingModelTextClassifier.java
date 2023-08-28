package dev.langchain4j.classification;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.util.*;

import static dev.langchain4j.internal.ValidationUtils.*;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

/**
 * A {@link TextClassifier} that uses an {@link EmbeddingModel} and predefined examples to perform classification.
 * Classification is done by comparing the embedding of the text being classified with the embeddings of predefined examples.
 * The classification quality improves with a greater number of examples for each label.
 * <p>
 * Example:
 * <pre>{@code
 * enum Sentiment {
 *     POSITIVE, NEUTRAL, NEGATIVE
 * }
 *
 *  Map<Sentiment, List<String>> examples = Map.of(
 *     POSITIVE, List.of("This is great!", "Wow, awesome!"),
 *     NEUTRAL,  List.of("Well, it's fine", "It's ok"),
 *     NEGATIVE, List.of("It is pretty bad", "Worst experience ever!")
 * );
 *
 * EmbeddingModel embeddingModel = new InProcessEmbeddingModel(ALL_MINILM_L6_V2_Q);
 *
 * TextClassifier<Sentiment> classifier = new EmbeddingModelTextClassifier<>(embeddingModel, examples);
 *
 * List<Sentiment> sentiments = classifier.classify("Awesome!");
 * System.out.println(sentiments); // [POSITIVE]
 * }</pre>
 *
 * @param <E> Enum that is the result of classification.
 */
public class EmbeddingModelTextClassifier<E extends Enum<E>> implements TextClassifier<E> {

    private final EmbeddingModel embeddingModel;
    private final Map<E, List<Embedding>> exampleEmbeddings;
    private final int maxResults;
    private final double minScore;
    private final double meanToMaxScoreRatio;

    /**
     * Creates a classifier with the default values for {@link #maxResults} (1), {@link #minScore} (0)
     * and {@link #meanToMaxScoreRatio} (0.5).
     *
     * @param embeddingModel The embedding model used for embedding both the examples and the text to be classified.
     * @param examples       A map containing examples of texts for each label. The more examples, the better.
     */
    public EmbeddingModelTextClassifier(EmbeddingModel embeddingModel, Map<E, ? extends Collection<String>> examples) {
        this(embeddingModel, examples, 1, 0, 0.5);
    }

    /**
     * Creates a classifier.
     *
     * @param embeddingModel      The embedding model used for embedding both the examples and the text to be classified.
     * @param examples            A map containing examples of texts for each label. The more examples, the better.
     * @param maxResults          The maximum number of labels to return for each classification.
     * @param minScore            The minimum similarity score required for classification, in the range [0..1].
     *                            Labels scoring lower than this value will be discarded.
     * @param meanToMaxScoreRatio A ratio, in the range [0..1], between the mean and max scores used for calculating
     *                            the final score.
     *                            During classification, the embeddings of examples for each label are compared to
     *                            the embedding of the text being classified.
     *                            This results in two metrics: the mean and max scores.
     *                            The mean score is the average similarity score for all examples associated with a given label.
     *                            The max score is the highest similarity score, corresponding to the example most
     *                            similar to the text being classified.
     *                            A value of 0 means that only the mean score will be used for ranking labels.
     *                            A value of 0.5 means that both scores will contribute equally to the final score.
     *                            A value of 1 means that only the max score will be used for ranking labels.
     */
    public EmbeddingModelTextClassifier(EmbeddingModel embeddingModel,
                                        Map<E, ? extends Collection<String>> examples,
                                        int maxResults,
                                        double minScore,
                                        double meanToMaxScoreRatio) {
        this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        ensureNotNull(examples, "examples");

        this.exampleEmbeddings = new HashMap<>();
        for (
                Map.Entry<E, ? extends Collection<String>> entry : examples.entrySet()) {
            List<Embedding> embeddings = new ArrayList<>();
            for (String example : entry.getValue()) {
                embeddings.add(embeddingModel.embed(example));
            }
            exampleEmbeddings.put(entry.getKey(), embeddings);
        }

        this.maxResults = ensureGreaterThanZero(maxResults, "maxResults");
        this.minScore = ensureBetween(minScore, 0.0, 1.0, "minScore");
        this.meanToMaxScoreRatio = ensureBetween(meanToMaxScoreRatio, 0.0, 1.0, "meanToMaxScoreRatio");
    }

    @Override
    public List<E> classify(String text) {

        Embedding textEmbedding = embeddingModel.embed(text);

        List<LabelWithScore> labelsWithScores = new ArrayList<>();
        for (Map.Entry<E, List<Embedding>> entry : exampleEmbeddings.entrySet()) {
            E label = entry.getKey();
            List<Embedding> examples = entry.getValue();

            double meanScore = 0;
            double maxScore = 0;
            for (Embedding example : examples) {
                double score = RelevanceScore.cosine(textEmbedding.vector(), example.vector());
                meanScore += score;
                maxScore = Math.max(score, maxScore);
            }
            meanScore /= examples.size();

            labelsWithScores.add(new LabelWithScore(label, aggregatedScore(meanScore, maxScore)));
        }

        return labelsWithScores.stream()
                .filter(it -> it.score >= minScore)
                .sorted(comparingDouble(labelWithScore -> 1 - labelWithScore.score)) // desc order
                .limit(maxResults)
                .map(it -> it.label)
                .collect(toList());
    }

    private double aggregatedScore(double meanScore, double maxScore) {
        return (meanToMaxScoreRatio * meanScore) + ((1 - meanToMaxScoreRatio) * maxScore);
    }

    private class LabelWithScore {

        private final E label;
        private final double score;

        private LabelWithScore(E label, double score) {
            this.label = label;
            this.score = score;
        }
    }
}
