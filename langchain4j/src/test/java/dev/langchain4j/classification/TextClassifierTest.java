package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class TextClassifierTest implements WithAssertions {
    public enum Categories {
        CAT, DOG, FISH
    }

    public static class CatClassifier implements TextClassifier<Categories> {
        @Override
        public List<LabelWithScore<Categories>> classifyWithScore(String text) {

            Set<LabelWithScore<Categories>> result = new HashSet<>();
            if (text.contains("cat")) {
                result.add(new LabelWithScore<>(Categories.CAT, 1.0));
            }
            if (text.contains("dog")) {
                result.add(new LabelWithScore<>(Categories.DOG, 1.0));
            }
            if (text.contains("fish")) {
                result.add(new LabelWithScore<>(Categories.FISH, 1.0));
            }
            return new ArrayList<>(result);
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
    void test_classify_with_score() {
        CatClassifier classifier = new CatClassifier();

        List<LabelWithScore<Categories>> results = classifier.classifyWithScore("cat fish");
        assertThat(results.stream()
                .map(LabelWithScore::getLabel)
                .collect(Collectors.toList())).containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.stream()
                .map(LabelWithScore::getScore)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);

        results = classifier.classifyWithScore(TextSegment.from("cat fish"));
        assertThat(results.stream()
                .map(LabelWithScore::getLabel)
                .collect(Collectors.toList())).containsOnly(Categories.CAT, Categories.FISH);
        assertThat(results.stream()
                .map(LabelWithScore::getScore)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);

        results = classifier.classifyWithScore(Document.from("dog cat"));
        assertThat(results.stream()
                .map(LabelWithScore::getLabel)
                .collect(Collectors.toList())).containsOnly(Categories.DOG, Categories.CAT);
        assertThat(results.stream()
                .map(LabelWithScore::getScore)
                .collect(Collectors.toList())).allMatch(score -> score == 1.0);
    }

}