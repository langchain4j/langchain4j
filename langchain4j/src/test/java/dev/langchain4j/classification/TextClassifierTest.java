package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

class TextClassifierTest implements WithAssertions {
    public enum Categories {
        CAT, DOG, FISH
    }

    public static class CatClassifier implements TextClassifier<Categories> {
        @Override
        public ClassificationResult<Categories> classifyWithDetail(String text) {

            Set<ScoredLabel<Categories>> scoredLabels = new HashSet<>();
            if (text.contains("cat")) {
                scoredLabels.add(ScoredLabel.from(Categories.CAT, 1.0));
            }
            if (text.contains("dog")) {
                scoredLabels.add(ScoredLabel.from(Categories.DOG, 1.0));
            }
            if (text.contains("fish")) {
                scoredLabels.add(ScoredLabel.from(Categories.FISH, 1.0));
            }
            return ClassificationResult.fromScoredLabels(scoredLabels);
        }
    }

    @Test
    void test_classify() {
        CatClassifier classifier = new CatClassifier();

        assertThat(classifier.classify("cat fish")).containsOnly(Categories.CAT, Categories.FISH);

        assertThat(classifier.classify(TextSegment.from("dog fish")))
                .containsOnly(Categories.DOG, Categories.FISH);

        assertThat(classifier.classify(Document.from("dog cat"))).containsOnly(Categories.CAT, Categories.DOG);
    }

    @Test
    void test_classify_with_detail() {
        CatClassifier classifier = new CatClassifier();

        ClassificationResult<Categories> results = classifier.classifyWithDetail("cat fish");
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::label)
                .collect(Collectors.toList())).containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::score)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);

        results = classifier.classifyWithDetail(TextSegment.from("cat fish"));
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::label)
                .collect(Collectors.toList())).containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::score)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);

        results = classifier.classifyWithDetail(Document.from("dog cat"));
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::label)
                .collect(Collectors.toList())).containsOnly(Categories.DOG, Categories.CAT);
        assertThat(results.scoredLabels().stream()
                .map(ScoredLabel::score)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);
    }

}