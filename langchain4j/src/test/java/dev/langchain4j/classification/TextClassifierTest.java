package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class TextClassifierTest implements WithAssertions {

    public enum Categories {
        CAT,
        DOG,
        FISH
    }

    public static class CatClassifier implements TextClassifier<Categories> {

        @Override
        public ClassificationResult<Categories> classifyWithScores(String text) {
            List<ScoredLabel<Categories>> scoredLabels = new ArrayList<>();
            if (text.contains("cat")) {
                scoredLabels.add(new ScoredLabel<>(Categories.CAT, 1.0));
            }
            if (text.contains("dog")) {
                scoredLabels.add(new ScoredLabel<>(Categories.DOG, 1.0));
            }
            if (text.contains("fish")) {
                scoredLabels.add(new ScoredLabel<>(Categories.FISH, 1.0));
            }
            return new ClassificationResult<>(scoredLabels);
        }
    }

    @Test
    void classify() {
        CatClassifier classifier = new CatClassifier();

        assertThat(classifier.classify("cat fish")).containsOnly(Categories.CAT, Categories.FISH);

        assertThat(classifier.classify(TextSegment.from("dog fish"))).containsOnly(Categories.DOG, Categories.FISH);

        assertThat(classifier.classify(Document.from("dog cat"))).containsOnly(Categories.CAT, Categories.DOG);
    }

    @Test
    void classify_with_scores() {
        CatClassifier classifier = new CatClassifier();

        ClassificationResult<Categories> results = classifier.classifyWithScores("cat fish");
        assertThat(results.scoredLabels().stream().map(ScoredLabel::label).collect(Collectors.toList()))
                .containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.scoredLabels().stream().map(ScoredLabel::score).collect(Collectors.toList()))
                .allMatch(score -> score == 1.0);

        results = classifier.classifyWithScores(TextSegment.from("cat fish"));
        assertThat(results.scoredLabels().stream().map(ScoredLabel::label).collect(Collectors.toList()))
                .containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.scoredLabels().stream().map(ScoredLabel::score).collect(Collectors.toList()))
                .allMatch(score -> score == 1.0);

        results = classifier.classifyWithScores(Document.from("dog cat"));
        assertThat(results.scoredLabels().stream().map(ScoredLabel::label).collect(Collectors.toList()))
                .containsOnly(Categories.DOG, Categories.CAT);
        assertThat(results.scoredLabels().stream().map(ScoredLabel::score).collect(Collectors.toList()))
                .allMatch(score -> score == 1.0);
    }
}
