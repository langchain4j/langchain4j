package dev.langchain4j.classification;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class TextClassifierTest implements WithAssertions {
    public enum Categories {
        CAT, DOG, FISH
    }

    public static class CatClassifier implements TextClassifier<Categories> {
        @Override
        public List<Categories> classify(String text) {
            Set<Categories> result = new HashSet<>();
            if (text.contains("cat")) {
                result.add(Categories.CAT);
            }
            if (text.contains("dog")) {
                result.add(Categories.DOG);
            }
            if (text.contains("fish")) {
                result.add(Categories.FISH);
            }
            return new ArrayList<>(result);
        }
    }

    @Test
    public void test() {
        CatClassifier classifier = new CatClassifier();

        assertThat(classifier.classify("cat fish")).containsOnly(Categories.CAT, Categories.FISH);

        assertThat(classifier.classify(TextSegment.from("dog fish")))
                .containsOnly(Categories.DOG, Categories.FISH);

        assertThat(classifier.classify(Document.from("dog cat"))).containsOnly(Categories.CAT, Categories.DOG);
    }

}